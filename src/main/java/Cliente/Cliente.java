package Cliente;

import Core.GestorArchivos;

import java.nio.channels.*;
import java.nio.*;
import java.net.*;
import java.util.Iterator;
import java.util.List;
import java.io.*;

public class Cliente {
    private static final String serverHost = "127.0.0.1";
    private static final int serverPort = 7777;
    private static final String baseDirectory = "./Cliente/Archivos";

    // Instanciamos el gestor para manejar el entorno local del cliente
    private static GestorArchivos gestor = new GestorArchivos(baseDirectory);

    public static void main(String[] args) {
        try {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);

            Selector selector = Selector.open();
            socketChannel.connect(new InetSocketAddress(serverHost, serverPort));
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()) {
                    SelectionKey selectionKey = (SelectionKey) iterator.next();
                    iterator.remove();

                    if (selectionKey.isConnectable()) {
                        SocketChannel channel = (SocketChannel) selectionKey.channel();
                        if (channel.isConnectionPending()) {
                            try {
                                channel.finishConnect();
                                System.out.println("Conectado exitosamente al servidor.");
                            } catch (Exception e) {
                                System.out.println("Error al conectar: " + e.getMessage());
                                return;
                            }
                        }
                        channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        continue;
                    }

                    // --- ENTRADA DE COMANDOS ---
                    if (selectionKey.isWritable()) {
                        System.out.println("\nEscriba un comando (o 'exit' para salir)...");
                        System.out.print(gestor.getDirectorioActual() + " > ");

                        String line = consoleReader.readLine();
                        if (line == null || line.trim().isEmpty()) continue;

                        String[] partes = line.split(" ", 4);
                        String argumento = partes[0];
                        String argumento1 = (partes.length > 1) ? partes[1] : "";
                        String argumento2 = (partes.length > 2) ? partes[2] : "";

                        // --- COMANDOS LOCALES (Usando GestorArchivos) ---
                        if (argumento.equals("ls")) {
                            System.out.println("--- Archivos Locales ---");
                            List<String> archivos = gestor.listarArchivos();
                            if (archivos.isEmpty()) System.out.println("(Directorio vacío)");
                            else archivos.forEach(System.out::println);
                            continue;

                        } else if (argumento.equals("cd")) {
                            if (!gestor.moverDirectorio(argumento1)) {
                                System.out.println("Directorio no encontrado o ya estás en la raíz.");
                            }
                            continue;

                        } else if (argumento.equals("touch")) {
                            if (gestor.crearArchivo(argumento1)) System.out.println("Archivo creado.");
                            else System.out.println("El archivo ya existe o hubo un error.");
                            continue;

                        } else if (argumento.equals("mkdir")) {
                            if (gestor.crearCarpeta(argumento1)) System.out.println("Carpeta creada.");
                            else System.out.println("La carpeta ya existe o hubo un error.");
                            continue;

                        } else if (argumento.equals("mv")) {
                            if (gestor.renombrar(argumento1, argumento2)) System.out.println("Renombrado exitoso.");
                            else System.out.println("Error al renombrar.");
                            continue;

                        } else if (argumento.equals("rm")) {
                            if (gestor.eliminarArchivo(argumento1)) System.out.println("Archivo eliminado.");
                            else System.out.println("No se pudo eliminar el archivo.");
                            continue;

                        } else if (argumento.equals("rmdir")) {
                            if (gestor.eliminarCarpeta(argumento1)) System.out.println("Carpeta eliminada.");
                            else System.out.println("No se pudo eliminar la carpeta.");
                            continue;

                        } else if (argumento.equals("cls")) {
                            clearTerminal();
                            continue;

                            // --- COMANDOS DE RED (Cliente -> Servidor) ---
                        } else if (argumento.equals("cp")) {
                            enviarArchivoAlServidor(argumento1, socketChannel);
                            continue;
                        } else if (argumento.equals("cpdir")) {
                            enviarCarpetaAlServidor(argumento1, socketChannel);
                            continue;

                        } else if (argumento.equalsIgnoreCase("exit")) {
                            System.out.println("Cerrando cliente...");
                            ByteBuffer bb = ByteBuffer.wrap(line.getBytes());
                            socketChannel.write(bb);
                            socketChannel.close();
                            System.exit(0);

                            // --- COMANDOS AL SERVIDOR (Servidor -> Cliente u otras peticiones) ---
                        } else if (argumento.equals("SRV")) {
                            SocketChannel channel = (SocketChannel) selectionKey.channel();

                            // Reconstruimos el comando quitando "SRV "
                            String comandoServidor = line.substring(4);
                            channel.write(ByteBuffer.wrap(comandoServidor.getBytes()));

                            selectionKey.interestOps(SelectionKey.OP_READ);
                            selector.select();

                            if (selectionKey.isReadable()) {
                                if (argumento1.equals("cp")) {
                                    recibirArchivoDelServidor(argumento2, channel);
                                } else if (argumento1.equals("cpdir")) {
                                    recibirCarpetaDelServidor(argumento2, channel);
                                } else {
                                    ByteBuffer readBuffer = ByteBuffer.allocate(2000);
                                    int bytesRead = channel.read(readBuffer);
                                    if (bytesRead > 0) {
                                        readBuffer.flip();
                                        System.out.println(new String(readBuffer.array(), 0, bytesRead));
                                    }
                                }
                                selectionKey.interestOps(SelectionKey.OP_WRITE);
                            }
                            continue;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------------------------- MÉTODOS DE TRANSFERENCIA (CLIENTE -> SERVIDOR) ---------------------------- //

    private static void enviarArchivoAlServidor(String fileName, SocketChannel socketChannel) throws IOException {
        File file = gestor.obtenerArchivo(fileName);
        if (!file.exists() || !file.isFile()) {
            System.out.println("El archivo local no existe.");
            return;
        }

        String command = "SRV_RECV_FILE " + fileName;
        socketChannel.write(ByteBuffer.wrap(command.getBytes()));

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
        System.out.println("Archivo enviado al servidor.");
    }

    private static void enviarCarpetaAlServidor(String folderName, SocketChannel socketChannel) throws IOException {
        String zipName = "temp_send_" + folderName + ".zip";
        String zipPath = gestor.getDirectorioActual() + "/" + zipName;

        if (!gestor.comprimirCarpeta(folderName, zipPath)) {
            System.out.println("La carpeta local no existe o falló la compresión.");
            return;
        }

        File zip = gestor.obtenerArchivo(zipName);
        String command = "SRV_RECV_DIR " + folderName;
        socketChannel.write(ByteBuffer.wrap(command.getBytes()));

        ByteBuffer sizeBuffer = ByteBuffer.allocate(Long.BYTES);
        sizeBuffer.putLong(zip.length());
        sizeBuffer.flip();
        socketChannel.write(sizeBuffer);

        try (FileInputStream fis = new FileInputStream(zip); FileChannel fileChannel = fis.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            while (fileChannel.read(buffer) > 0) {
                buffer.flip();
                socketChannel.write(buffer);
                buffer.clear();
            }
        }

        gestor.eliminarArchivo(zipName); // Limpiar el temporal local
        System.out.println("Carpeta enviada al servidor.");
    }

    // ---------------------------- MÉTODOS DE RECEPCIÓN (SERVIDOR -> CLIENTE) ---------------------------- //

    private static void recibirArchivoDelServidor(String fileName, SocketChannel socketChannel) throws IOException {
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
        System.out.println("Archivo recibido exitosamente del servidor.");
    }

    private static void recibirCarpetaDelServidor(String folderName, SocketChannel socketChannel) throws IOException {
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
        System.out.println("Carpeta recibida y descomprimida exitosamente del servidor.");
    }

    private static void clearTerminal() {
        try {
            new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
        } catch (Exception e) {
            // Ignorar en caso de no estar en Windows o fallar
        }
    }
}