import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Server extends Thread implements Closeable {
    private final DatagramSocket socket;
    private final int port;
    private final byte[] buf = new byte[256];
    private final Set<SocketAddress> clients = new HashSet<>();
    private final Map<SocketAddress, String> nicknames = new HashMap<>();
    private final List<Message> messageHistory = new ArrayList<>(MAX_HISTORY);
    private static final int MAX_HISTORY = 100;
    private volatile boolean running = true;

    public Server(int port) throws SocketException, UnknownHostException {
        this.port = port;
        socket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"));
        setName("Server");
    }

    @Override
    public void run() {
        System.out.println("Server started on port " + port);
        System.out.println("Server IP address: " + getServerIpAddress());

        while (running) {
            var packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
                var address = packet.getAddress();
                var port = packet.getPort();
                var clientAddr = new InetSocketAddress(address, port);

                var received = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                var message = Message.fromNetworkString(received, nicknames.getOrDefault(clientAddr, "Unknown"));

                switch (message.getType()) {
                    case HELLO:
                        handleHello(clientAddr, address, port, message.getContent());
                        break;
                    case LEAVE:
                        handleLeave(clientAddr, address, port, message.getContent());
                        break;
                    case USER_LIST:
                        handleUserList(address, port);
                        break;
                    case CHAT_PRIVATE:
                        handlePrivateMessage(clientAddr, message);
                        break;
                    case CHAT:
                    case SYSTEM:
                    default:
                        handleMessage(clientAddr, message);
                        break;
                }
            } catch (SocketException se) {
                if (!running) break;
            } catch (IOException e) {
                System.err.println("Error processing packet: " + e.getMessage());
            }
        }
        socket.close();
    }

    private void handleUserList(InetAddress address, int port) throws IOException {
        var sb = new StringBuilder("Connected users:\n\n");
        for (var entry : nicknames.entrySet()) {
            var addr = (InetSocketAddress) entry.getKey();
            sb.append("• ").append(entry.getValue())
                    .append(" (").append(addr.getAddress().getHostAddress()).append(")\n");
        }

        var response = new Message(sb.toString(), "Server", Message.Type.USER_LIST_RESPONSE);
        sendPacket(response.toNetworkString(), address, port);
    }

    @Override
    public void close() {
        running = false;
        socket.close();
    }

    public String getServerIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    private void handleHello(SocketAddress clientAddr, InetAddress address, int port, String nickname) throws IOException {
        clients.add(clientAddr);
        if (nickname.isEmpty()) {
            nickname = address.getHostAddress() + ":" + port;
        }
        nicknames.put(clientAddr, nickname);

        var historyBuilder = new StringBuilder();
        for (Message message : messageHistory) {
            historyBuilder.append(message.toNetworkString()).append("||");
        }

        var personalWelcomeMsg = new Message("Welcome to the chat, " + nickname + "!", "Server", Message.Type.SYSTEM);
        sendPacket(personalWelcomeMsg.toNetworkString(), address, port);

        var welcomeMsg = new Message(historyBuilder.toString(), "Server", Message.Type.WELCOME);
        sendPacket(welcomeMsg.toNetworkString(), address, port);
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        var joinMsg = new Message(nickname + " (" + address.getHostAddress() + ") joined the chat.", "Server", Message.Type.JOIN);
        addMessageToHistory(joinMsg);
        broadcast(joinMsg.toNetworkString(), null);
    }

    private void handleLeave(SocketAddress clientAddr, InetAddress address, int port, String nickname) throws IOException {
        clients.remove(clientAddr);
        var resolvedNickname = nicknames.getOrDefault(clientAddr, address.getHostAddress() + ":" + port);
        nicknames.remove(clientAddr);

        var leaveMsg = new Message(resolvedNickname + " (" + address.getHostAddress() + ") left the chat.", "Server", Message.Type.LEAVE);
        addMessageToHistory(leaveMsg);
        broadcast(leaveMsg.toNetworkString(), clientAddr);
    }

    private void handleMessage(SocketAddress clientAddr, Message message) throws IOException {
        var nickname = nicknames.get(clientAddr);
        if (nickname == null) {
            nickname = ((InetSocketAddress) clientAddr).getAddress().getHostAddress() + ":" + ((InetSocketAddress) clientAddr).getPort();
            nicknames.put(clientAddr, nickname);
        }

        var finalMessage = new Message(message.getContent(), nickname, message.getTimestamp(), message.getType());
        addMessageToHistory(finalMessage);

        broadcast(finalMessage.toNetworkString(), clientAddr);
    }

    private void handlePrivateMessage(SocketAddress clientAddr, Message message) {
        var nickname = nicknames.get(clientAddr);
        if (nickname == null) {
            nickname = ((InetSocketAddress) clientAddr).getAddress().getHostAddress() + ":" + ((InetSocketAddress) clientAddr).getPort();
        }

        var clientIP = ((InetSocketAddress) clientAddr).getAddress().getHostAddress();

        try {
            var privateContent = "Private message from " + nickname + " (" + clientIP + "): " + message.getContent();

            for (SocketAddress client : clients) {
                var inetClient = (InetSocketAddress) client;
                if (inetClient.getAddress().isLoopbackAddress()) {
                    var privateMsg = new Message(privateContent, "Server", Message.Type.SYSTEM);
                    sendPacket(privateMsg.toNetworkString(), inetClient.getAddress(), inetClient.getPort());
                    break;
                }
            }

            var confirmMsg = new Message("Private message received", "Server", Message.Type.SYSTEM);
            var inetAddress = ((InetSocketAddress) clientAddr).getAddress();
            var port = ((InetSocketAddress) clientAddr).getPort();
            sendPacket(confirmMsg.toNetworkString(), inetAddress, port);
        } catch (IOException e) {
            System.err.println("Failed to process private message: " + e.getMessage());
        }
    }

    private void broadcast(String msg, SocketAddress sender) throws IOException {
        var data = msg.getBytes(StandardCharsets.UTF_8);
        for (SocketAddress client : clients) {
            if (client.equals(sender)) continue;
            var inetClient = (InetSocketAddress) client;
            sendPacket(data, inetClient.getAddress(), inetClient.getPort());
        }
    }

    private void addMessageToHistory(Message message) {
        if (messageHistory.size() >= MAX_HISTORY) messageHistory.remove(0);
        messageHistory.add(message);
    }

    private void sendPacket(String msg, InetAddress address, int port) throws IOException {
        sendPacket(msg.getBytes(StandardCharsets.UTF_8), address, port);
    }

    private void sendPacket(byte[] data, InetAddress address, int port) throws IOException {
        var packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }
}