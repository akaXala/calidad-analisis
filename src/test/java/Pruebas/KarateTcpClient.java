package Pruebas;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class KarateTcpClient {
    private SocketChannel socketChannel;
    private final String host;
    private final int port;

    public KarateTcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void conectar() throws IOException {
        socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress(host, port));
        // Lo configuramos como bloqueante porque para las pruebas queremos
        // esperar a que el servidor responda antes de hacer el "Assert"
        socketChannel.configureBlocking(true);
    }

    public String enviarComando(String comando) throws IOException {
        if (socketChannel == null || !socketChannel.isConnected()) {
            throw new IOException("No hay conexión con el servidor");
        }

        // Enviamos el comando
        ByteBuffer buffer = ByteBuffer.wrap(comando.getBytes());
        socketChannel.write(buffer);

        // Leemos la respuesta del servidor
        ByteBuffer readBuffer = ByteBuffer.allocate(4096);
        int bytesRead = socketChannel.read(readBuffer);

        if (bytesRead > 0) {
            readBuffer.flip();
            return new String(readBuffer.array(), 0, bytesRead).trim();
        }

        return "";
    }

    public void desconectar() throws IOException {
        if (socketChannel != null && socketChannel.isOpen()) {
            socketChannel.close();
        }
    }
}