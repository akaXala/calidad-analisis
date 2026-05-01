package Cliente;

import Core.GestorArchivos;

import java.nio.channels.*;
import java.nio.*;
import java.net.*;
import java.util.Iterator;
import java.util.List;
import java.io.*;

public class Cliente {
    // 1. Constantes corregidas a UPPER_SNAKE_CASE
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 7777;
    private static final String BASE_DIRECTORY = "./Cliente/Archivos";

    private static final GestorArchivos gestor = new GestorArchivos(BASE_DIRECTORY);

    private static volatile boolean running = true; // FIX: Condición de salida

    public static void main(String[] args) {
        // 2. Simplificación del bloque principal extrayendo a submétodos
        try (SocketChannel socketChannel = SocketChannel.open();
             Selector selector = Selector.open()) {

            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT));
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

            while (running) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();

                    if (selectionKey.isConnectable()) {
                        finalizarConexion(selectionKey, selector);
                    } else if (selectionKey.isWritable()) {
                        procesarEntradaUsuario(consoleReader, socketChannel, selectionKey, selector);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error en el cliente: " + e.getMessage());
        }
    }

    private static void finalizarConexion(SelectionKey selectionKey, Selector selector) throws IOException {
        SocketChannel channel = (SocketChannel) selectionKey.channel();
        if (channel.isConnectionPending()) {
            try {
                channel.finishConnect();
                System.out.println("Conectado exitosamente al servidor.");
            } catch (Exception e) {
                System.out.println("Error al conectar: " + e.getMessage());
                System.exit(1);
            }
        }
        channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    private static void procesarEntradaUsuario(BufferedReader consoleReader, SocketChannel socketChannel, SelectionKey selectionKey, Selector selector) throws IOException {
        System.out.println("\nEscriba un comando (o 'exit' para salir)...");
        System.out.print(gestor.getDirectorioActual() + " > ");

        String line = consoleReader.readLine();
        if (line == null || line.trim().isEmpty()) return;

        String[] partes = line.split(" ", 4);
        String argumento = partes[0];
        String argumento1 = (partes.length > 1) ? partes[1] : "";
        String argumento2 = (partes.length > 2) ? partes[2] : "";

        if (argumento.equals("SRV")) {
            enviarComandoSrv(line, argumento1, argumento2, selectionKey, selector);
            return;
        }

        ejecutarComandoLocalORed(line, argumento, argumento1, argumento2, socketChannel);
    }

    // 3. Switch en lugar de muchísimos 'if-else' encadenados
    private static void ejecutarComandoLocalORed(String line, String argumento, String argumento1, String argumento2, SocketChannel socketChannel) throws IOException {
        switch (argumento) {
            case "ls":
                manejarLsLocal();
                break;
            case "cd":
                if (!gestor.moverDirectorio(argumento1)) System.out.println("Directorio no encontrado o ya estás en la raíz.");
                break;
            case "touch":
                System.out.println(gestor.crearArchivo(argumento1) ? "Archivo creado." : "El archivo ya existe o hubo un error.");
                break;
            case "mkdir":
                System.out.println(gestor.crearCarpeta(argumento1) ? "Carpeta creada." : "La carpeta ya existe o hubo un error.");
                break;
            case "mv":
                System.out.println(gestor.renombrar(argumento1, argumento2) ? "Renombrado exitoso." : "Error al renombrar.");
                break;
            case "rm":
                System.out.println(gestor.eliminarArchivo(argumento1) ? "Archivo eliminado." : "No se pudo eliminar el archivo.");
                break;
            case "rmdir":
                System.out.println(gestor.eliminarCarpeta(argumento1) ? "Carpeta eliminada." : "No se pudo eliminar la carpeta.");
                break;
            case "cls":
                clearTerminal();
                break;
            case "cp":
                enviarArchivoAlServidor(argumento1, socketChannel);
                break;
            case "cpdir":
                enviarCarpetaAlServidor(argumento1, socketChannel);
                break;
            case "exit":
                salirDelCliente(line, socketChannel);
                break;
            default:
                System.out.println("Comando no reconocido localmente.");
                break;
        }
    }

    private static void manejarLsLocal() {
        System.out.println("--- Archivos Locales ---");
        List<String> archivos = gestor.listarArchivos();
        if (archivos.isEmpty()) System.out.println("(Directorio vacío)");
        else archivos.forEach(System.out::println);
    }

    private static void salirDelCliente(String line, SocketChannel socketChannel) throws IOException {
        System.out.println("Cerrando cliente...");
        ByteBuffer bb = ByteBuffer.wrap(line.getBytes());
        socketChannel.write(bb);
        socketChannel.close();
        System.exit(0);
    }

    private static void enviarComandoSrv(String line, String argumento1, String argumento2, SelectionKey selectionKey, Selector selector) throws IOException {
        SocketChannel channel = (SocketChannel) selectionKey.channel();
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // FIX: Restaurar estado de interrupción
        } catch (Exception e) {
            // Ignorar en caso de no estar en Windows
        }
    }
}