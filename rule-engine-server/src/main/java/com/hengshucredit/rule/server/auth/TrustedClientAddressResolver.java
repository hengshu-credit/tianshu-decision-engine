package com.hengshucredit.rule.server.auth;

import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 仅在直连来源属于可信代理时接受转发地址。 */
@Component
public class TrustedClientAddressResolver {
    private final List<Cidr> trustedProxies;

    public TrustedClientAddressResolver(ProjectAuthProperties properties) {
        this.trustedProxies = parseCidrs(properties == null
                ? Collections.<String>emptyList() : properties.getTrustedProxyCidrs());
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalizeAddress(request == null ? null : request.getRemoteAddr());
        if (remote == null || !matches(remote, trustedProxies)) return remote;
        List<String> forwarded = forwardedAddresses(request);
        if (forwarded.isEmpty()) return remote;
        forwarded.add(remote);
        for (int index = forwarded.size() - 1; index >= 0; index--) {
            String address = normalizeAddress(forwarded.get(index));
            if (address != null && !matches(address, trustedProxies)) return address;
        }
        return normalizeAddress(forwarded.get(0));
    }

    public boolean matchesIp(String address, List<String> cidrs) {
        return matches(normalizeAddress(address), parseCidrs(cidrs));
    }

    public boolean matchesHost(String presentedHost, List<String> allowedHosts) {
        if (allowedHosts == null || allowedHosts.isEmpty()) return true;
        String host = normalizeHost(presentedHost);
        if (host == null) return false;
        for (String allowed : allowedHosts) {
            String candidate = normalizeHost(allowed);
            if (candidate == null) continue;
            if (candidate.startsWith("*.")) {
                String suffix = candidate.substring(1);
                if (host.endsWith(suffix) && host.length() > suffix.length()) return true;
            } else if (host.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidCidr(String value) {
        try {
            Cidr.parse(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private List<String> forwardedAddresses(HttpServletRequest request) {
        String forwarded = request.getHeader("Forwarded");
        List<String> result = new ArrayList<>();
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            for (String element : forwarded.split(",")) {
                for (String part : element.split(";")) {
                    String trimmed = part.trim();
                    if (trimmed.regionMatches(true, 0, "for=", 0, 4)) {
                        result.add(unquote(trimmed.substring(4).trim()));
                        break;
                    }
                }
            }
            return result;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null) Collections.addAll(result, xff.split(","));
        return result;
    }

    private String normalizeHost(String value) {
        if (value == null) return null;
        String host = value.trim().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return null;
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            if (end < 0) return null;
            host = host.substring(1, end);
        } else {
            int colon = host.lastIndexOf(':');
            if (colon > 0 && host.indexOf(':') == colon && digits(host.substring(colon + 1))) {
                host = host.substring(0, colon);
            }
        }
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.isEmpty() ? null : host;
    }

    private String normalizeAddress(String value) {
        if (value == null) return null;
        String address = unquote(value.trim());
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end < 0) return null;
            address = address.substring(1, end);
        } else {
            int colon = address.lastIndexOf(':');
            if (colon > 0 && address.indexOf(':') == colon && digits(address.substring(colon + 1))) {
                address = address.substring(0, colon);
            }
        }
        byte[] bytes = literalAddress(address);
        if (bytes == null) return null;
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private boolean matches(String address, List<Cidr> cidrs) {
        byte[] bytes = literalAddress(address);
        if (bytes == null) return false;
        for (Cidr cidr : cidrs) if (cidr.matches(bytes)) return true;
        return false;
    }

    private List<Cidr> parseCidrs(List<String> values) {
        List<Cidr> result = new ArrayList<>();
        if (values == null) return result;
        for (String value : values) result.add(Cidr.parse(value));
        return result;
    }

    private static byte[] literalAddress(String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.indexOf(':') >= 0) {
            if (!value.matches("[0-9A-Fa-f:.]+")) return null;
            try {
                byte[] bytes = InetAddress.getByName(value).getAddress();
                return bytes.length == 16 ? bytes : null;
            } catch (UnknownHostException e) {
                return null;
            }
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] bytes = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            if (!digits(parts[index])) return null;
            int number;
            try {
                number = Integer.parseInt(parts[index]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (number < 0 || number > 255) return null;
            bytes[index] = (byte) number;
        }
        return bytes;
    }

    private static boolean digits(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return false;
        }
        return true;
    }

    private String unquote(String value) {
        return value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    private static class Cidr {
        private final byte[] network;
        private final int prefix;

        private Cidr(byte[] network, int prefix) {
            this.network = network;
            this.prefix = prefix;
        }

        private static Cidr parse(String value) {
            if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("CIDR 不能为空");
            String[] parts = value.trim().split("/", -1);
            if (parts.length > 2) throw new IllegalArgumentException("CIDR 格式错误");
            byte[] address = literalAddress(parts[0]);
            if (address == null) throw new IllegalArgumentException("IP 地址格式错误");
            int max = address.length * 8;
            int prefix = parts.length == 1 ? max : parsePrefix(parts[1], max);
            byte[] network = address.clone();
            for (int bit = prefix; bit < max; bit++) {
                network[bit / 8] = (byte) (network[bit / 8] & ~(1 << (7 - bit % 8)));
            }
            return new Cidr(network, prefix);
        }

        private static int parsePrefix(String value, int max) {
            try {
                int prefix = Integer.parseInt(value);
                if (prefix < 0 || prefix > max) throw new IllegalArgumentException("CIDR 前缀越界");
                return prefix;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("CIDR 前缀格式错误", e);
            }
        }

        private boolean matches(byte[] address) {
            if (address == null || address.length != network.length) return false;
            for (int bit = 0; bit < prefix; bit++) {
                int mask = 1 << (7 - bit % 8);
                if ((address[bit / 8] & mask) != (network[bit / 8] & mask)) return false;
            }
            return true;
        }
    }
}
