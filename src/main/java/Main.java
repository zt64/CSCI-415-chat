import javax.swing.*;
import java.io.IOException;

public class Main {
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;

    public static void main(String[] args) {
        var frame = new JFrame("Chat App");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);

        var options = new String[]{"Create Server", "Connect to Server"};
        var choice = JOptionPane.showOptionDialog(frame, "Do you want to create a server or connect to one?", "Chat App", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, null);

        if (choice == JOptionPane.CLOSED_OPTION) System.exit(0);

        Client client = null;
        Server server = null;
        String nickname = null;

        if (choice == 0) {
            var port = promptForPort(frame);
            if (port == null) System.exit(0);

            try {
                server = new Server(port);
                server.start();

                var serverIP = server.getServerIpAddress();
                JOptionPane.showMessageDialog(frame,
                    "Server started successfully!\n" +
                    "Your server IP address: " + serverIP + "\n" +
                    "Port: " + port + "\n\n" +
                    "Other users should use this IP address to connect.",
                    "Server Info", JOptionPane.INFORMATION_MESSAGE);

                nickname = promptForNickname(frame);
                if (nickname == null) System.exit(0);
                client = new Client("localhost", port, nickname);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Error: Failed to start server or connect client: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
        } else if (choice == 1) {
            var ip = promptForIP(frame);
            if (ip == null) System.exit(0);

            var port = promptForPort(frame);
            if (port == null) System.exit(0);

            try {
                nickname = promptForNickname(frame);
                if (nickname == null) System.exit(0);
                client = new Client(ip, port, nickname);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame,
                    "Error: Failed to connect to server: " + e.getMessage() + "\n\n" +
                    "Please check:\n" +
                    "1. The server is running\n" +
                    "2. You're using the correct IP address (not 'localhost' for remote servers)\n" +
                    "3. The port number is correct\n" +
                    "4. Any firewalls are configured to allow the connection",
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
        } else {
            System.exit(0);
        }

        var chatScreen = new ChatScreen(client, nickname);
        chatScreen.setSize(900, 900);
        chatScreen.setLocationRelativeTo(null);
        chatScreen.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        chatScreen.setVisible(true);
        chatScreen.startReceiver();

        var finalClient = client;
        var finalServer = server;
        chatScreen.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (finalServer != null) finalServer.close();
                finalClient.close();
                chatScreen.dispose();
                System.exit(0);
            }
        });
    }

    private static Integer promptForPort(JFrame frame) {
        while (true) {
            var portInput = JOptionPane.showInputDialog(frame, "Enter port number (" + MIN_PORT + "-" + MAX_PORT + "):", "5050");
            if (portInput == null) return null;
            try {
                var port = Integer.parseInt(portInput.trim());
                if (port < MIN_PORT || port > MAX_PORT) {
                    JOptionPane.showMessageDialog(frame, "Error: Port must be between " + MIN_PORT + " and " + MAX_PORT + ".", "Error", JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                return port;
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(frame, "Error: Invalid port number. Please enter a valid integer.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static String promptForIP(JFrame frame) {
        while (true) {
            var message =
                "Enter server IP address:\n\n" +
                "Note: Use 'localhost' only if the server is on this machine.\n" +
                "For remote servers, use their actual IP address (e.g., 192.168.1.5)";

            var ip = JOptionPane.showInputDialog(frame, message, "localhost");
            if (ip == null) return null;
            ip = ip.trim();
            if (ip.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Error: IP address cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                continue;
            }

            return ip;
        }
    }

    private static String promptForNickname(JFrame frame) {
        while (true) {
            var nickname = JOptionPane.showInputDialog(frame, "Enter your nickname:");
            if (nickname == null) return null;
            nickname = nickname.trim();
            if (nickname.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Error: Nickname cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            return nickname;
        }
    }
}