package Servidor;

import Core.GestorArchivos;

import java.nio.channels.*;
import java.nio.*;
import java.net.*;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.*;

public class Servidor {
    private static final int serverPort = 7777;
    private static final String baseDirectory = "./Servidor/Archivos";

    // Mapa para guardar la sesión (el Gestor de Archivos) de cada cliente conectado de forma independiente
    private static Map<SocketChannel, GestorArchivos> sesionesClientes = new HashMap<>();

    public static void main(String[] args) {
        try {
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.configureBlocking(false);
            serverSocket.socket().bind(new InetSocketAddress(serverPort));

            Selector selector = Selector.open();
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Servidor esperando conexiones en el puerto " + serverPort + "...");

            while (true) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()) {
                    SelectionKey selectionKey = (SelectionKey) iterator.next();
                    iterator.remove();

                    // --- NUEVA CONEXIÓN ---
                    if (selectionKey.isAcceptable()) {
                        SocketChannel socketChannel = serverSocket.accept();
                        socketChannel.configureBlocking(false);
                        socketChannel.register(selector, SelectionKey.OP_READ);

                        // Le asignamos su propio GestorArchivos aislado
                        sesionesClientes.put(socketChannel, new GestorArchivos(baseDirectory));

                        System.out.println("Cliente conectado: " + socketChannel.socket().getRemoteSocketAddress());
                        continue;
                    }

                    // --- LECTURA DE COMANDOS ---
                    if (selectionKey.isReadable()) {
                        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                        GestorArchivos gestor = sesionesClientes.get(socketChannel); // Recuperamos su sesión

                        ByteBuffer bb = ByteBuffer.allocate(2000);
                        int n;
                        try {
                            n = socketChannel.read(bb);
                        } catch (IOException e) {
                            n = -1; // Forzar cierre si hay error de conexión
                        }

                        // Si el cliente se desconectó abruptamente
                        if (n == -1) {
                            System.out.println("Cliente desconectado: " + socketChannel.socket().getRemoteSocketAddress());
                            sesionesClientes.remove(socketChannel);
                            socketChannel.close();
                            continue;
                        }

                        bb.flip();
                        String line = new String(bb.array(), 0, n).trim();
                        String[] parts = line.split(" ", 4);
                        String command = parts[0];
                        String argument = (parts.length > 1) ? parts[1] : "";
                        String argument1 = (parts.length > 2) ? parts[2] : "";

                        System.out.println("Petición de " + socketChannel.socket().getRemoteSocketAddress() + ": " + line);
                        StringBuilder sb = new StringBuilder("\nSERVIDOR: ");

                        // --- PROCESAMIENTO DE COMANDOS (Usando GestorArchivos) ---
                        if (command.equals("ls")) {
                            sb.append("--- Archivos en el servidor ---\n");
                            sb.append("Ruta: ").append(gestor.getDirectorioActual()).append("\n");
                            List<String> archivos = gestor.listarArchivos();
                            if (archivos.isEmpty()) sb.append("(Directorio vacío)\n");
                            else archivos.forEach(a -> sb.append(a).append("\n"));
                            enviarRespuesta(socketChannel, sb.toString());

                        } else if (command.equals("cd")) {
                            if (gestor.moverDirectorio(argument)) {
                                sb.append("Ruta actual: ").append(gestor.getDirectorioActual());
                            } else {
                                sb.append("El directorio no existe o ya estás en la raíz.");
                            }
                            enviarRespuesta(socketChannel, sb.toString());

                        } else if (command.equals("touch")) {
                            if (gestor.crearArchivo(argument)) sb.append("Archivo creado exitosamente.");
                            else sb.append("No se pudo crear (quizás ya existe).");
                            enviarRespuesta(socketChannel, sb.toString());

                        } else if (command.equals("mkdir")) {
                            if (gestor.crearCarpeta(argument)) sb.append("Carpeta creada exitosamente.");
                            else sb.append("No se pudo crear (quizás ya existe).");
                            enviarRespuesta(socketChannel, sb.toString());

                        } else if (command.equals("mv")) {
                            if (gestor.renombrar(argument, argument1)) sb.append("Renombrado correctamente.");
                            else sb.append("Error al renombrar.");
                            enviarRespuesta(socketChannel, sb.toString());

                        } else if (command.equals("rm")) {
                            if (gestor.eliminarArchivo(argument)) sb.append("Archivo eliminado.");
                            else sb.append("No se pudo eliminar el archivo.");
                            enviarRespuesta(socketChannel, sb.toString());

                        } else if (command.equals("rmdir")) {
                            if (gestor.eliminarCarpeta(argument)) sb.append("Carpeta eliminada.");
                            else sb.append("No se pudo eliminar la carpeta.");
                            enviarRespuesta(socketChannel, sb.toString());

                            // --- TRANSFERENCIA DE ARCHIVOS ---
                        } else if (command.equals("cp")) {
                            // Servidor a Cliente
                            enviarArchivo(argument, gestor, socketChannel);
                        } else if (command.equals("cpdir")) {
                            // Servidor a Cliente
                            enviarCarpeta(argument, gestor, socketChannel);

                        } else if (line.startsWith("SRV_RECV_FILE ")) {
                            // Cliente a Servidor (nota: cambié un poco el trigger para diferenciarlo mejor)
                            recibirArchivo(argument, gestor, socketChannel);
                        } else if (line.startsWith("SRV_RECV_DIR ")) {
                            // Cliente a Servidor
                            recibirCarpeta(argument, gestor, socketChannel);

                        } else if (command.equals("exit")) {
                            System.out.println("Cliente desconectado: " + socketChannel.socket().getRemoteSocketAddress());
                            sesionesClientes.remove(socketChannel);
                            socketChannel.close();
                        } else {
                            enviarRespuesta(socketChannel, sb.append("Comando no reconocido.").toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- FUNCIONES AUXILIARES DE RED ---

    private static void enviarRespuesta(SocketChannel socketChannel, String mensaje) throws IOException {
        socketChannel.write(ByteBuffer.wrap(mensaje.getBytes()));
    }

    private static void enviarArchivo(String fileName, GestorArchivos gestor, SocketChannel socketChannel) throws IOException {
        File file = gestor.obtenerArchivo(fileName);
        if (!file.exists() || !file.isFile()) {
            enviarRespuesta(socketChannel, "SERVIDOR: El archivo no existe\n");
            return;
        }

        // Enviar tamaño
        ByteBuffer sizeBuffer = ByteBuffer.allocate(Long.BYTES);
        sizeBuffer.putLong(file.length());
        sizeBuffer.flip();
        socketChannel.write(sizeBuffer);

        // Enviar bytes
        try (FileInputStream fis = new FileInputStream(file); FileChannel fileChannel = fis.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (fileChannel.read(buffer) > 0) {
                buffer.flip();
                socketChannel.write(buffer);
                buffer.clear();
            }
        }
        System.out.println("Archivo enviado.");
    }

    private static void enviarCarpeta(String folderName, GestorArchivos gestor, SocketChannel socketChannel) throws IOException {
        String zipName = gestor.getDirectorioActual() + "/temp_" + folderName + ".zip";

        if (!gestor.comprimirCarpeta(folderName, zipName)) {
            enviarRespuesta(socketChannel, "SERVIDOR: La carpeta no existe o falló la compresión\n");
            return;
        }

        File zipFile = new File(zipName);
        ByteBuffer sizeBuffer = ByteBuffer.allocate(Long.BYTES);
        sizeBuffer.putLong(zipFile.length());
        sizeBuffer.flip();
        socketChannel.write(sizeBuffer);

        try (FileInputStream fis = new FileInputStream(zipFile); FileChannel fileChannel = fis.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (fileChannel.read(buffer) > 0) {
                buffer.flip();
                socketChannel.write(buffer);
                buffer.clear();
            }
        }
        gestor.eliminarArchivo("temp_" + folderName + ".zip"); // Limpiar temporal
        System.out.println("Carpeta enviada.");
    }

    private static void recibirArchivo(String fileName, GestorArchivos gestor, SocketChannel socketChannel) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(Long.BYTES);
        socketChannel.read(sizeBuffer);
        sizeBuffer.flip();
        long fileSize = sizeBuffer.getLong();

        File file = gestor.obtenerArchivo(fileName);
        try (FileOutputStream fos = new FileOutputStream(file); FileChannel fileChannel = fos.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            long bytesReceived = 0;
            while (bytesReceived < fileSize) {
                int read = socketChannel.read(buffer);
                if (read == -1) break;
                buffer.flip();
                fileChannel.write(buffer);
                bytesReceived += read;
                buffer.clear();
            }
        }
        System.out.println("Archivo recibido: " + fileName);
    }

    private static void recibirCarpeta(String folderName, GestorArchivos gestor, SocketChannel socketChannel) throws IOException {
        ByteBuffer sizeBuffer = ByteBuffer.allocate(Long.BYTES);
        socketChannel.read(sizeBuffer);
        sizeBuffer.flip();
        long fileSize = sizeBuffer.getLong();

        String zipName = "temp_recv_" + folderName + ".zip";
        File zipFile = gestor.obtenerArchivo(zipName);

        try (FileOutputStream fos = new FileOutputStream(zipFile); FileChannel fileChannel = fos.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            long bytesReceived = 0;
            while (bytesReceived < fileSize) {
                int read = socketChannel.read(buffer);
                if (read == -1) break;
                buffer.flip();
                fileChannel.write(buffer);
                bytesReceived += read;
                buffer.clear();
            }
        }

        gestor.descomprimirCarpeta(zipFile.getAbsolutePath(), gestor.getDirectorioActual() + "/" + folderName);
        gestor.eliminarArchivo(zipName);
        System.out.println("Carpeta recibida y descomprimida: " + folderName);
    }
}