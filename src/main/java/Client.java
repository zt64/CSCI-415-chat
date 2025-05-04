import java.io.IOException;
import java.io.Closeable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Client implements Closeable {
    private final DatagramSocket socket;
    private final InetAddress address;
    private final int port;
    private final String nickname;
    private static final int MAX_HISTORY = 100;
    private final String[] messageHistory = new String[MAX_HISTORY];
    private int historyCount = 0;
    private final List<Message> systemMessages = new ArrayList<>();

    public Client(String host, int port, String nickname) throws IOException {
        try {
            socket = new DatagramSocket();
            address = InetAddress.getByName(host);
            this.port = port;
            this.nickname = nickname;
            
            socket.setSoTimeout(5000);

            var handshakeMsg = new Message(nickname, "", Message.Type.HELLO);
            var handshakeBuf = handshakeMsg.toNetworkString().getBytes(StandardCharsets.UTF_8);
            var handshakePacket = new DatagramPacket(handshakeBuf, handshakeBuf.length, address, port);
            socket.send(handshakePacket);

            var buf = new byte[2048];
            var responsePacket = new DatagramPacket(buf, buf.length);

            try {
                socket.receive(responsePacket);
                var response = new String(responsePacket.getData(), 0, responsePacket.getLength(), StandardCharsets.UTF_8);
                var welcomeMsg = Message.fromNetworkString(response, "Server");
                
                if (welcomeMsg.getType() == Message.Type.SYSTEM) {
                    systemMessages.add(welcomeMsg);
                }

                socket.receive(responsePacket);
                response = new String(responsePacket.getData(), 0, responsePacket.getLength(), StandardCharsets.UTF_8);
                welcomeMsg = Message.fromNetworkString(response, "Server");
                if (welcomeMsg.getType() != Message.Type.WELCOME) {
                    throw new IOException("Server did not respond with expected handshake.");
                }

                var msgs = welcomeMsg.getContent().split("\\|\\|");
                historyCount = 0;
                for (String m : msgs) {
                    if (!m.isEmpty() && historyCount < MAX_HISTORY) {
                        messageHistory[historyCount++] = m;
                    }
                }
            } catch (SocketTimeoutException e) {
                throw new IOException("Connection timed out. Server might be unreachable or blocked by a firewall.");
            } finally {
                socket.setSoTimeout(1000);  // Set to 1 second after connection
            }
        } catch (IOException e) {
            throw e;
        }
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public void sendMessage(Message message) throws IOException {
        var buf = message.toNetworkString().getBytes(StandardCharsets.UTF_8);
        var packet = new DatagramPacket(buf, buf.length, address, port);
        socket.send(packet);
    }

    public void sendEcho(String msg) throws IOException {
        var message = new Message(msg, nickname, Message.Type.CHAT);
        sendMessage(message);
    }

    public void sendPrivateToServer(String msg) throws IOException {
        var message = new Message(msg, nickname, Message.Type.PRIVATE_TO_SERVER);
        sendMessage(message);
    }

    public Message receive() throws IOException {
        var packet = new DatagramPacket(new byte[1024], 1024);
        socket.receive(packet);

        var received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        return Message.fromNetworkString(received, "Server");
    }

    public String getNickname() {
        return nickname;
    }

    public String[] getHistory() {
        var result = new String[historyCount];
        System.arraycopy(messageHistory, 0, result, 0, historyCount);
        return result;
    }

    public List<Message> getSystemMessages() {
        return systemMessages;
    }

    @Override
    public void close() {
        if (socket.isClosed()) {
            return;
        }

        try {
            socket.setSoTimeout(100);

            var leaveMsg = new Message(nickname, "", Message.Type.LEAVE);
            var buf = leaveMsg.toNetworkString().getBytes(StandardCharsets.UTF_8);
            var packet = new DatagramPacket(buf, buf.length, address, port);
            socket.send(packet);
        } catch (Exception ignored) {
        } finally {
            socket.close();
        }
    }
}