package nl.hauntedmc.featureframework.velocity.log;

import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.featureframework.toolkit.text.format.ComponentFormatter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/** Structured connection-event logging shared by Velocity feature hosts. */
public final class ConnectionLogHelper {
    private ConnectionLogHelper() {
    }

    public static void logPreLoginDenied(
            FeatureLogger logger,
            String cause,
            InboundConnection connection,
            String username,
            String... extraFields
    ) {
        if (logger == null) return;
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        put(fields, "username", username);
        appendConnectionFields(fields, connection);
        logger.info(buildMessage("pre_login_denied", cause, fields, extraFields));
    }

    public static void logLoginDenied(FeatureLogger logger, String cause, Player player, String... extraFields) {
        if (logger != null) {
            logger.info(buildMessage("login_denied", cause, basePlayerFields(player), extraFields));
        }
    }

    public static void logServerConnectDenied(
            FeatureLogger logger,
            String cause,
            Player player,
            String... extraFields
    ) {
        if (logger == null) return;
        LinkedHashMap<String, String> fields = basePlayerFields(player);
        put(fields, "current_server", currentServerName(player));
        logger.info(buildMessage("server_connect_denied", cause, fields, extraFields));
    }

    public static void logDisconnect(FeatureLogger logger, String cause, Player player, String... extraFields) {
        if (logger == null) return;
        LinkedHashMap<String, String> fields = basePlayerFields(player);
        put(fields, "current_server", currentServerName(player));
        logger.info(buildMessage("disconnect", cause, fields, extraFields));
    }

    public static String plain(Component component) {
        if (component == null) return null;
        return ComponentFormatter.serialize(component)
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build();
    }

    private static LinkedHashMap<String, String> basePlayerFields(Player player) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        put(fields, "username", player.getUsername());
        put(fields, "uuid", String.valueOf(player.getUniqueId()));
        appendConnectionFields(fields, player);
        return fields;
    }

    private static void appendConnectionFields(Map<String, String> fields, InboundConnection connection) {
        if (connection == null) return;
        put(fields, "ip", resolveIp(connection.getRemoteAddress()));
        put(fields, "virtual_host", connection.getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .orElseGet(() -> connection.getRawVirtualHost().orElse(null)));
        if (connection.getProtocolVersion() != null) {
            put(fields, "protocol", String.valueOf(connection.getProtocolVersion().getProtocol()));
            put(fields, "protocol_name", connection.getProtocolVersion().toString());
        }
    }

    private static String currentServerName(Player player) {
        return player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
    }

    private static String resolveIp(InetSocketAddress address) {
        if (address == null) return null;
        InetAddress inetAddress = address.getAddress();
        return inetAddress == null ? address.getHostString() : inetAddress.getHostAddress();
    }

    private static String buildMessage(
            String event,
            String cause,
            LinkedHashMap<String, String> fields,
            String... extraFields
    ) {
        if ((extraFields.length & 1) != 0) {
            throw new IllegalArgumentException("Extra connection log fields must be key/value pairs.");
        }
        StringBuilder builder = new StringBuilder();
        builder.append("event=").append(quote(event));
        builder.append(" cause=").append(quote(cause));
        fields.forEach((key, value) -> appendField(builder, key, value));
        for (int index = 0; index < extraFields.length; index += 2) {
            appendField(builder, extraFields[index], extraFields[index + 1]);
        }
        return builder.toString();
    }

    private static void appendField(StringBuilder builder, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) return;
        builder.append(' ').append(key).append('=').append(quote(value));
    }

    private static void put(Map<String, String> fields, String key, String value) {
        if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }

    private static String quote(String value) {
        if (value == null) return "\"\"";
        boolean needsQuotes = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character) || character == '=' || character == '"' || character == '\\') {
                needsQuotes = true;
                break;
            }
        }
        if (!needsQuotes) return value;
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
