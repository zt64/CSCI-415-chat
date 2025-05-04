import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class Message {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");
    private final String content;
    private final String sender;
    private final Instant timestamp;
    private final Type type;

    public enum Type {
        CHAT,
        CHAT_PRIVATE,
        JOIN,
        LEAVE,
        SYSTEM,
        HELLO,
        WELCOME,
        USER_LIST,
        USER_LIST_RESPONSE,
    }

    public Message(String content, String sender, Type type) {
        this(content, sender, Instant.now(), type);
    }

    public Message(String content, String sender, Instant timestamp, Type type) {
        this.content = Objects.requireNonNull(content);
        this.sender = Objects.requireNonNull(sender);
        this.timestamp = Objects.requireNonNull(timestamp);
        this.type = Objects.requireNonNull(type);
    }

    public String getContent() {
        return content;
    }

    public String getSender() {
        return sender;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Type getType() {
        return type;
    }

    public String toNetworkString() {
        return String.join(":", type.name(), String.valueOf(timestamp.toEpochMilli()), sender, content);
    }

    public static Message fromNetworkString(String networkMsg, String defaultSender) {
        var parts = networkMsg.split(":", 4);
        if (parts.length < 4) return new Message(networkMsg, defaultSender, Type.CHAT);

        try {
            var type = Type.valueOf(parts[0]);
            var timestamp = Instant.ofEpochMilli(Long.parseLong(parts[1]));
            var sender = parts[2];
            var content = parts[3];
            return new Message(content, sender, timestamp, type);
        } catch (Exception e) {
            return new Message(networkMsg, defaultSender, Type.CHAT);
        }
    }

    @Override
    public String toString() {
        return String.format("[%s] %s%s",
                LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER),
                type == Type.CHAT ? sender + ": " : "",
                content);
    }
}