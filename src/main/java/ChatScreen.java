import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatScreen extends JFrame {
    private final Client client;
    private final String nickname;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");
    private volatile boolean running = true;
    private Thread receiverThread;

    private JTextField inputField;
    private JButton sendButton;
    private JTextPane textPane;

    private Style defaultStyle;
    private Style timestampStyle;
    private Style systemStyle;
    private Style joinStyle;
    private Style leaveStyle;
    private Style chatStyle;
    private Style privateStyle;

    public ChatScreen(Client client, String nickname) {
        this.client = client;
        this.nickname = nickname;

        initializeUI();

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
    }

    private void initializeUI() {
        setLayout(new BorderLayout());
        setTitle("Chat");

        addWindowListener(
            new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent e) {
                    inputField.requestFocusInWindow();
                }
            }
        );

        var headerPanel = new JPanel(new BorderLayout());
        var headerLabel = new JLabel(
            "<html>Connected as: <b>" + nickname + "</b><br>Server: " + client.getAddress().getHostAddress() + ":" + client.getPort() + "</html>",
            SwingConstants.CENTER
        );
        headerLabel.setFont(new Font("Arial", Font.BOLD, 14));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        headerPanel.setBackground(new Color(0x2E86C1));
        headerLabel.setForeground(Color.WHITE);
        headerPanel.add(headerLabel, BorderLayout.CENTER);

        var btnPanel = new JPanel();
        btnPanel.setBackground(new Color(0x2E86C1));

        var connectedBtn = new JButton("Connected Users");
        connectedBtn.setFont(new Font("Arial", Font.BOLD, 12));
        connectedBtn.addActionListener(e -> {
            try {
                var message = new Message("", nickname, Message.Type.USER_LIST);
                client.sendMessage(message);
            } catch (IOException ex) {
                appendText("[" + LocalDateTime.now().format(TIMESTAMP_FORMATTER) + "] Error: " + ex.getMessage() + "\n", defaultStyle);
            }
        });

        var privateBtn = new JButton("Private to Server");
        privateBtn.setFont(new Font("Arial", Font.BOLD, 12));
        privateBtn.addActionListener(e -> {
            var msg = JOptionPane.showInputDialog(this, "Enter private message to server:");
            if (msg != null && !msg.trim().isEmpty()) {
                try {
                    client.sendPrivateToServer(msg);

                    appendTimestamp();
                    appendText("Private message sent: " + msg + "\n", privateStyle);
                } catch (IOException ex) {
                    appendTimestamp();
                    appendText("Error: " + ex.getMessage() + "\n", defaultStyle);
                }
            }
        });

        var saveBtn = new JButton("Save Chat");
        saveBtn.setFont(new Font("Arial", Font.BOLD, 12));
        saveBtn.addActionListener(e -> saveChat());

        btnPanel.add(connectedBtn);
        btnPanel.add(privateBtn);
        btnPanel.add(saveBtn);
        headerPanel.add(btnPanel, BorderLayout.EAST);

        textPane = new JTextPane();
        textPane.setEditable(false);
        
        initializeTextStyles();
        
        textPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Chat"));

        for (var sysMsg : client.getSystemMessages()) {
            appendMessage(sysMsg);
        }

        var history = client.getHistory();
        for (var msg : history) {
            var message = Message.fromNetworkString(msg, "Server");
            appendMessage(message);
        }

        inputField = new JTextField();
        inputField.setFont(new Font("Arial", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xCCCCCC)),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Arial", Font.BOLD, 14));
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());

        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                sendButton.setEnabled(!inputField.getText().trim().isEmpty());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                sendButton.setEnabled(!inputField.getText().trim().isEmpty());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                sendButton.setEnabled(!inputField.getText().trim().isEmpty());
            }
        });

        inputField.addActionListener(e -> sendMessage());

        var inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(() -> inputField.requestFocusInWindow());
    }

    private void initializeTextStyles() {
        StyleContext styleContext = StyleContext.getDefaultStyleContext();
        var doc = textPane.getStyledDocument();

        defaultStyle = styleContext.getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setFontFamily(defaultStyle, "Arial");
        StyleConstants.setFontSize(defaultStyle, 14);

        timestampStyle = doc.addStyle("timestamp", defaultStyle);
        StyleConstants.setForeground(timestampStyle, new Color(0x555555));
        StyleConstants.setBold(timestampStyle, true);
        
        systemStyle = doc.addStyle("system", defaultStyle);
        StyleConstants.setForeground(systemStyle, new Color(0x0066CC));
        StyleConstants.setBold(systemStyle, true);
        StyleConstants.setFontSize(systemStyle, 15);
        
        joinStyle = doc.addStyle("join", defaultStyle);
        StyleConstants.setForeground(joinStyle, new Color(0x2E8B57));
        StyleConstants.setBold(joinStyle, true);
        StyleConstants.setFontSize(joinStyle, 15);
        
        leaveStyle = doc.addStyle("leave", defaultStyle);
        StyleConstants.setForeground(leaveStyle, new Color(0xCC0000));
        StyleConstants.setBold(leaveStyle, true);
        StyleConstants.setFontSize(leaveStyle, 15);
        
        chatStyle = doc.addStyle("chat", defaultStyle);
        StyleConstants.setForeground(chatStyle, Color.BLACK);
        
        privateStyle = doc.addStyle("private", defaultStyle);
        StyleConstants.setForeground(privateStyle, new Color(0x8A2BE2)); // Purple
        StyleConstants.setBold(privateStyle, true);
        StyleConstants.setItalic(privateStyle, true);
    }
    
    private void appendText(String text, Style style) {
        var doc = textPane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        textPane.setCaretPosition(doc.getLength());
    }
    
    private void appendTimestamp() {
        String timestamp = "[" + LocalDateTime.now().format(TIMESTAMP_FORMATTER) + "] ";
        appendText(timestamp, timestampStyle);
    }
    
    private void appendMessage(Message message) {
        String timestamp = "[" + message.getTimestamp().format(TIMESTAMP_FORMATTER) + "] ";
        appendText(timestamp, timestampStyle);
        
        Style contentStyle;
        String content;
        
        switch (message.getType()) {
            case SYSTEM:
                contentStyle = systemStyle;
                content = message.getContent() + "\n";
                break;
            case JOIN:
                contentStyle = joinStyle;
                content = message.getContent() + "\n";
                break;
            case LEAVE:
                contentStyle = leaveStyle;
                content = message.getContent() + "\n";
                break;
            case CHAT:
                contentStyle = chatStyle;
                content = message.getSender() + ": " + message.getContent() + "\n";
                break;
            default:
                contentStyle = defaultStyle;
                content = message.getContent() + "\n";
        }
        
        appendText(content, contentStyle);
    }

    private void sendMessage() {
        var msg = inputField.getText();
        if (msg.trim().isEmpty()) return;
        try {
            client.sendEcho(msg);

            var selfMessage = new Message(msg, nickname, Message.Type.CHAT);
            appendMessage(selfMessage);

            inputField.setText("");
        } catch (IOException ex) {
            appendTimestamp();
            appendText("Error: " + ex.getMessage() + "\n", defaultStyle);
        }
    }

    private void saveChat() {
        var fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Chat History");
        
        var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        fileChooser.setSelectedFile(new File("chat_history_" + timestamp + ".txt"));
        
        int userSelection = fileChooser.showSaveDialog(this);
        
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            
            if (!fileToSave.getName().toLowerCase().endsWith(".txt")) {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".txt");
            }
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave))) {
                writer.write("Chat History - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                writer.newLine();
                writer.write("User: " + nickname);
                writer.newLine();
                writer.write("Server: " + client.getAddress().getHostAddress() + ":" + client.getPort());
                writer.newLine();
                writer.write("---------------------------------------------");
                writer.newLine();
                writer.newLine();
                
                var doc = textPane.getDocument();
                writer.write(doc.getText(0, doc.getLength()));
                
                JOptionPane.showMessageDialog(this,
                    "Chat history saved successfully to:\n" + fileToSave.getAbsolutePath(), 
                    "Save Successful", 
                    JOptionPane.INFORMATION_MESSAGE);
                    
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                    "Error saving chat history: " + ex.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void startReceiver() {
        receiverThread = new Thread(() -> {
            while (running) {
                try {
                    var message = client.receive();
                    if (!running) break;

                    SwingUtilities.invokeLater(() -> {
                        if (message.getType() == Message.Type.USER_LIST_RESPONSE) {
                            JOptionPane.showMessageDialog(
                                this,
                                message.getContent(),
                                "Connected Users",
                                JOptionPane.INFORMATION_MESSAGE
                            );
                        } else {
                            appendMessage(message);
                        }
                    });
                } catch (SocketTimeoutException e) {
                    if (!running) break;
                } catch (IOException ignored) {
                    if (!running) break;

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
        }, "ChatReceiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public void cleanup() {
        running = false;

        if (client != null) {
            client.close();
        }

        if (receiverThread != null && receiverThread.isAlive()) {
            receiverThread.interrupt();

            try {
                receiverThread.join(200);
            } catch (InterruptedException ignored) {
            }
        }
    }
}