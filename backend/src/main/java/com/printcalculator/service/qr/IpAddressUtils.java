package com.printcalculator.service.qr;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

final class IpAddressUtils {

    private IpAddressUtils() {
    }

    static String resolveClientIp(String forwardedFor,
                                  String realIp,
                                  String remoteAddress,
                                  boolean trustProxyHeaders,
                                  List<IpAddressMatcher> trustedProxyMatchers) {
        String normalizedRemoteAddress = normalizeIp(remoteAddress);
        if (trustProxyHeaders && isTrustedProxy(normalizedRemoteAddress, trustedProxyMatchers)) {
            String forwardedClientIp = firstValidIpFromForwardedFor(forwardedFor);
            if (forwardedClientIp != null) {
                return forwardedClientIp;
            }

            String normalizedRealIp = normalizeIp(realIp);
            if (normalizedRealIp != null) {
                return normalizedRealIp;
            }
        }

        if (normalizedRemoteAddress != null) {
            return normalizedRemoteAddress;
        }
        return "unknown";
    }

    static List<IpAddressMatcher> parseTrustedProxyMatchers(String trustedProxyNetworks) {
        return Arrays.stream(String.valueOf(trustedProxyNetworks == null ? "" : trustedProxyNetworks).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(IpAddressMatcher::new)
                .toList();
    }

    static String normalizeIp(String candidate) {
        String normalized = normalizeCandidate(candidate);
        if (normalized == null) {
            return null;
        }

        try {
            return InetAddress.getByName(normalized).getHostAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    static boolean isPublicIp(String candidate) {
        String normalized = normalizeIp(candidate);
        if (normalized == null) {
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(normalized);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return false;
            }

            if (address instanceof Inet4Address ipv4Address) {
                return !isCarrierGradeNat(ipv4Address);
            }
            if (address instanceof Inet6Address ipv6Address) {
                return !isUniqueLocalIpv6(ipv6Address);
            }
            return true;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    static boolean isTrustedProxy(String remoteAddress, List<IpAddressMatcher> trustedProxyMatchers) {
        if (remoteAddress == null || trustedProxyMatchers == null || trustedProxyMatchers.isEmpty()) {
            return false;
        }
        return trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(remoteAddress));
    }

    private static String firstValidIpFromForwardedFor(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }

        for (String rawPart : forwardedFor.split(",")) {
            String candidate = normalizeIp(rawPart);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String normalizeCandidate(String candidate) {
        if (candidate == null) {
            return null;
        }

        String value = candidate.trim();
        if (value.isEmpty() || "unknown".equalsIgnoreCase(value)) {
            return null;
        }

        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("[") && value.contains("]")) {
            value = value.substring(1, value.indexOf(']'));
        } else if (value.contains(".") && value.indexOf(':') == value.lastIndexOf(':')) {
            int portSeparator = value.lastIndexOf(':');
            if (portSeparator > 0) {
                value = value.substring(0, portSeparator);
            }
        }

        if (!looksLikeIpLiteral(value)) {
            return null;
        }
        return value;
    }

    private static boolean looksLikeIpLiteral(String value) {
        if (value.contains(":")) {
            return value.matches("[0-9A-Fa-f:%.]+");
        }
        return value.matches("[0-9.]+");
    }

    private static boolean isCarrierGradeNat(Inet4Address address) {
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 100 && second >= 64 && second <= 127;
    }

    private static boolean isUniqueLocalIpv6(Inet6Address address) {
        byte firstByte = address.getAddress()[0];
        return (firstByte & (byte) 0xfe) == (byte) 0xfc;
    }
}
