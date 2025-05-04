import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Client implements Closeable {
    private final DatagramSocket socket;
    private final InetAddress address;
    private final int port;
    private final String nickname;
    private static final int MAX_HISTORY = 100;
    private final List<String> messageHistory = new ArrayList<>(MAX_HISTORY);
    private final List<Message> systemMessages = new ArrayList<>();

    public Client(String host, int port, String nickname) throws IOException {
        socket = new DatagramSocket();
        address = InetAddress.getByName(host);
        this.port = port;
        this.nickname = nickname;

        socket.setSoTimeout(5000);
        performHandshake();
        socket.setSoTimeout(1000);
    }

    private void performHandshake() throws IOException {
        sendMessage(new Message(nickname, "", Message.Type.HELLO));

        var buf = new byte[2048];
        var responsePacket = new DatagramPacket(buf, buf.length);

        socket.receive(responsePacket);
        processServerMessage(responsePacket);

        socket.receive(responsePacket);
        processWelcomeMessage(responsePacket);
    }

    private void processServerMessage(DatagramPacket packet) {
        var response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        var welcomeMsg = Message.fromNetworkString(response, "Server");
        if (welcomeMsg.getType() == Message.Type.SYSTEM) {
            systemMessages.add(welcomeMsg);
        }
    }

    private void processWelcomeMessage(DatagramPacket packet) throws IOException {
        var response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        var welcomeMsg = Message.fromNetworkString(response, "Server");
        if (welcomeMsg.getType() != Message.Type.WELCOME) {
            throw new IOException("Server did not respond with expected handshake.");
        }
        loadMessageHistory(welcomeMsg.getContent());
    }

    private void loadMessageHistory(String historyContent) {
        messageHistory.clear();
        for (String m : historyContent.split("\\|\\|")) {
            if (!m.isEmpty() && messageHistory.size() < MAX_HISTORY) {
                messageHistory.add(m);
            }
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

    public void sendMessage(String msg) throws IOException {
        sendMessage(new Message(msg, nickname, Message.Type.CHAT));
    }

    public void sendPrivateMessage(String msg) throws IOException {
        sendMessage(new Message(msg, nickname, Message.Type.CHAT_PRIVATE));
    }

    public Message receive() throws IOException {
        var packet = new DatagramPacket(new byte[1024], 1024);
        socket.receive(packet);
        var received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
        return Message.fromNetworkString(received, "Server");
    }

    public String[] getHistory() {
        return messageHistory.toArray(new String[0]);
    }

    public List<Message> getSystemMessages() {
        return systemMessages;
    }

    @Override
    public void close() {
        if (!socket.isClosed()) {
            try {
                sendMessage(new Message(nickname, "", Message.Type.LEAVE));
            } catch (Exception ignored) {
            }
            socket.close();
        }
    }
}