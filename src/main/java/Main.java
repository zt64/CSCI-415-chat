import javax.swing.*;
import java.io.IOException;
import java.util.function.Function;

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
                        "Port: " + port + "\n\n",
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
        return promptForInput(frame, "Enter port number (" + MIN_PORT + "-" + MAX_PORT + "):", "5050", input -> {
            int port = Integer.parseInt(input.trim());
            if (port < MIN_PORT || port > MAX_PORT) throw new IllegalArgumentException("Port out of range");
            return port;
        });
    }

    private static String promptForIP(JFrame frame) {
        return promptForInput(frame, "Enter server IP address (e.g., 'localhost' or '192.168.1.5'):", "localhost", String::trim);
    }

    private static String promptForNickname(JFrame frame) {
        return promptForInput(frame, "Enter your nickname:", null, input -> {
            if (input.trim().isEmpty()) throw new IllegalArgumentException("Nickname cannot be empty");
            return input.trim();
        });
    }

    private static <T> T promptForInput(JFrame frame, String message, String defaultValue, Function<String, T> parser) {
        while (true) {
            var input = JOptionPane.showInputDialog(frame, message, defaultValue);
            if (input == null) return null;
            try {
                return parser.apply(input);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}