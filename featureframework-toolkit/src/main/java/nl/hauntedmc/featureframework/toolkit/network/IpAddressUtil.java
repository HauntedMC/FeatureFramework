package nl.hauntedmc.featureframework.toolkit.network;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Shared strict IP-literal parser. It never performs DNS resolution for hostnames. */
public final class IpAddressUtil {
    private IpAddressUtil() {
    }

    public static InetAddress parseLiteral(String input) {
        if (input == null || input.isBlank()) return null;
        String candidate = stripBrackets(input.trim());
        if (candidate.isEmpty()) return null;
        if (candidate.indexOf(':') >= 0) {
            try {
                return InetAddress.getByName(candidate);
            } catch (UnknownHostException ignored) {
                return null;
            }
        }
        byte[] ipv4 = parseIpv4(candidate);
        if (ipv4 == null) return null;
        try {
            return InetAddress.getByAddress(ipv4);
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    public static String normalizeLiteral(String input) {
        InetAddress address = parseLiteral(input);
        return address == null ? null : address.getHostAddress();
    }

    private static byte[] parseIpv4(String candidate) {
        String[] parts = candidate.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] bytes = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3) return null;
            int value = 0;
            for (int charIndex = 0; charIndex < part.length(); charIndex++) {
                char character = part.charAt(charIndex);
                if (!Character.isDigit(character)) return null;
                value = (value * 10) + (character - '0');
            }
            if (value > 255) return null;
            bytes[index] = (byte) value;
        }
        return bytes;
    }

    private static String stripBrackets(String candidate) {
        if (candidate.length() >= 2 && candidate.charAt(0) == '['
                && candidate.charAt(candidate.length() - 1) == ']') {
            return candidate.substring(1, candidate.length() - 1);
        }
        return candidate;
    }
}
