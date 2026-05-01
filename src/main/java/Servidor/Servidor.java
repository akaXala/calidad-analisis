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
    // 1. Nombres de constantes corregidos (Code Smell resuelto)
    private static final int SERVER_PORT = 7777;
    private static final String BASE_DIRECTORY = "./Servidor/Archivos";

    private static final Map<SocketChannel, GestorArchivos> sesionesClientes = new HashMap<>();

    private static volatile boolean running = true; // FIX: Condición de salida

    public static void main(String[] args) {
        // 2. Extraída la lógica compleja a submétodos
        try (ServerSocketChannel serverSocket = ServerSocketChannel.open();
             Selector selector = Selector.open()) {

            serverSocket.configureBlocking(false);
            serverSocket.socket().bind(new InetSocketAddress(SERVER_PORT));
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Servidor esperando conexiones en el puerto " + SERVER_PORT + "...");

            while (running) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();

                    if (selectionKey.isAcceptable()) {
                        aceptarConexion(serverSocket, selector);
                    } else if (selectionKey.isReadable()) {
                        procesarPeticionCliente(selectionKey);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }

    private static void aceptarConexion(ServerSocketChannel serverSocket, Selector selector) throws IOException {
        SocketChannel socketChannel = serverSocket.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
        sesionesClientes.put(socketChannel, new GestorArchivos(BASE_DIRECTORY));
        System.out.println("Cliente conectado: " + socketChannel.socket().getRemoteSocketAddress());
    }

    private static void procesarPeticionCliente(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        GestorArchivos gestor = sesionesClientes.get(socketChannel);
        ByteBuffer bb = ByteBuffer.allocate(2000);
        int n;

        try {
            n = socketChannel.read(bb);
        } catch (IOException e) {
            n = -1;
        }

        if (n == -1) {
            cerrarConexion(socketChannel);
            return;
        }

        bb.flip();
        String line = new String(bb.array(), 0, n).trim();
        System.out.println("Petición de " + socketChannel.socket().getRemoteSocketAddress() + ": " + line);

        ejecutarComando(line, socketChannel, gestor);
    }

    private static void ejecutarComando(String line, SocketChannel socketChannel, GestorArchivos gestor) throws IOException {
        String[] parts = line.split(" ", 4);
        String command = parts[0];
        String argument = (parts.length > 1) ? parts[1] : "";
        String argument1 = (parts.length > 2) ? parts[2] : "";
        StringBuilder sb = new StringBuilder("\nSERVIDOR: ");

        // Comandos de transferencia que interceptan peticiones específicas
        if (line.startsWith("SRV_RECV_FILE ")) {
            recibirArchivo(argument, gestor, socketChannel);
            return;
        } else if (line.startsWith("SRV_RECV_DIR ")) {
            recibirCarpeta(argument, gestor, socketChannel);
            return;
        }

        // 3. Uso de Switch en lugar de Ifs anidados para reducir CC
        switch (command) {
            case "ls":
                manejarLs(gestor, sb);
                enviarRespuesta(socketChannel, sb.toString());
                break;
            case "cd":
                if (gestor.moverDirectorio(argument)) sb.append("Ruta actual: ").append(gestor.getDirectorioActual());
                else sb.append("El directorio no existe o ya estás en la raíz.");
                enviarRespuesta(socketChannel, sb.toString());
                break;
            case "touch":
                sb.append(gestor.crearArchivo(argument) ? "Archivo creado exitosamente." : "No se pudo crear (quizás ya existe).");
                enviarRespuesta(socketChannel, sb.toString());
                break;
            case "mkdir":
                sb.append(gestor.crearCarpeta(argument) ? "Carpeta creada exitosamente." : "No se pudo crear (quizás ya existe).");
                enviarRespuesta(socketChannel, sb.toString());
                break;
            case "mv":
                sb.append(gestor.renombrar(argument, argument1) ? "Renombrado correctamente." : "Error al renombrar.");
                enviarRespuesta(socketChannel, sb.toString());
                break;
            case "rm":
                sb.append(gestor.eliminarArchivo(argument) ? "Archivo eliminado." : "No se pudo eliminar el archivo.");
                enviarRespuesta(socketChannel, sb.toString());
                break;
            case "rmdir":
                sb.append(gestor.eliminarCarpeta(argument) ? "Carpeta eliminada." : "No se pudo eliminar la carpeta.");
                enviarRespuesta(socketChannel, sb.toString());
                break;
            case "cp":
                enviarArchivo(argument, gestor, socketChannel);
                break;
            case "cpdir":
                enviarCarpeta(argument, gestor, socketChannel);
                break;
            case "exit":
                cerrarConexion(socketChannel);
                break;
            default:
                enviarRespuesta(socketChannel, sb.append("Comando no reconocido.").toString());
                break;
        }
    }

    private static void manejarLs(GestorArchivos gestor, StringBuilder sb) {
        sb.append("--- Archivos en el servidor ---\n");
        sb.append("Ruta: ").append(gestor.getDirectorioActual()).append("\n");
        List<String> archivos = gestor.listarArchivos();
        if (archivos.isEmpty()) sb.append("(Directorio vacío)\n");
        else archivos.forEach(a -> sb.append(a).append("\n"));
    }

    private static void cerrarConexion(SocketChannel socketChannel) throws IOException {
        System.out.println("Cliente desconectado: " + socketChannel.socket().getRemoteSocketAddress());
        sesionesClientes.remove(socketChannel);
        socketChannel.close();
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

        ByteBuffer sizeBuffer = ByteBuffer.allocate(Long.BYTES);
        sizeBuffer.putLong(file.length());
        sizeBuffer.flip();
        socketChannel.write(sizeBuffer);

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
        gestor.eliminarArchivo("temp_" + folderName + ".zip");
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