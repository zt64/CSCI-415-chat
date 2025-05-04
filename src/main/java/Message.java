import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class Message {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");
    
    public enum Type {
        CHAT,
        JOIN,
        LEAVE,
        SYSTEM,
        HELLO,
        WELCOME,
        USER_LIST,
        USER_LIST_RESPONSE,
        PRIVATE_TO_SERVER
    }

    private final String content;
    private final String sender;
    private final long timestampEpoch;
    private final Type type;

    public Message(String content, String sender, Type type) {
        this(content, sender, System.currentTimeMillis(), type);
    }

    public Message(String content, String sender, long timestampEpoch, Type type) {
        this.content = content;
        this.sender = sender;
        this.timestampEpoch = timestampEpoch;
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public String getSender() {
        return sender;
    }

    public long getTimestampEpoch() {
        return timestampEpoch;
    }

    public LocalDateTime getTimestamp() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampEpoch), ZoneId.systemDefault());
    }

    public Type getType() {
        return type;
    }

    public String toNetworkString() {
        return type.name() + ":" + timestampEpoch + ":" + sender + ":" + content;
    }

    public static Message fromNetworkString(String networkMsg, String defaultSender) {
        var parts = networkMsg.split(":", 4);
        if (parts.length >= 4) {
            try {
                var type = Type.valueOf(parts[0]);
                var timestamp = Long.parseLong(parts[1]);
                var sender = parts[2];
                var content = parts[3];
                return new Message(content, sender, timestamp, type);
            } catch (IllegalArgumentException e) {
                // Failed to parse
            }
        }

        var colonIndex = networkMsg.indexOf(':');
        if (colonIndex > 0) {
            try {
                var typeStr = networkMsg.substring(0, colonIndex);
                var type = Type.valueOf(typeStr);
                var content = networkMsg.substring(colonIndex + 1);

                if (content.startsWith("[")) {
                    var closeBracket = content.indexOf(']');
                    if (closeBracket > 0) {
                        var rest = content.substring(closeBracket + 1).trim();

                        var msgColonIndex = rest.indexOf(':');
                        if (msgColonIndex > 0 && type == Type.CHAT) {
                            var msgContent = rest.substring(msgColonIndex + 1).trim();
                            return new Message(msgContent, defaultSender, type);
                        } else {
                            return new Message(rest, defaultSender, type);
                        }
                    }
                }

                return new Message(content, defaultSender, type);
            } catch (IllegalArgumentException e) {
            }
        }

        return new Message(networkMsg, defaultSender, Type.CHAT);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s%s",
            getTimestamp().format(TIMESTAMP_FORMATTER),
            type == Type.CHAT ? sender + ": " : "",
            content);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var message = (Message) o;
        return timestampEpoch == message.timestampEpoch &&
               Objects.equals(content, message.content) &&
               Objects.equals(sender, message.sender) &&
               type == message.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, sender, timestampEpoch, type);
    }
}