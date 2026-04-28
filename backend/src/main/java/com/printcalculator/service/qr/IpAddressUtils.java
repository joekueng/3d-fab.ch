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
        return resolveClientIp(null, forwardedFor, realIp, remoteAddress, trustProxyHeaders, trustedProxyMatchers);
    }

    static String resolveClientIp(String forwarded,
                                  String forwardedFor,
                                  String realIp,
                                  String remoteAddress,
                                  boolean trustProxyHeaders,
                                  List<IpAddressMatcher> trustedProxyMatchers) {
        String normalizedRemoteAddress = normalizeIp(remoteAddress);
        if (trustProxyHeaders && isTrustedProxy(normalizedRemoteAddress, trustedProxyMatchers)) {
            String forwardedClientIp = clientIpFromProxyChain(parseForwardedForHeader(forwardedFor), trustedProxyMatchers);
            if (forwardedClientIp != null) {
                return forwardedClientIp;
            }

            forwardedClientIp = clientIpFromProxyChain(parseForwardedHeader(forwarded), trustedProxyMatchers);
            if (forwardedClientIp != null) {
                return forwardedClientIp;
            }

            String normalizedRealIp = normalizeIp(realIp);
            if (normalizedRealIp != null) {
                return normalizedRealIp;
            }
        }

        if (trustProxyHeaders
                && normalizedRemoteAddress != null
                && !isPublicIp(normalizedRemoteAddress)) {
            String forwardedClientIp = publicClientIpFromProxyHeaders(
                    forwarded,
                    forwardedFor,
                    realIp,
                    trustedProxyMatchers
            );
            if (forwardedClientIp != null) {
                return forwardedClientIp;
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

    private static String clientIpFromProxyChain(List<String> proxyChain, List<IpAddressMatcher> trustedProxyMatchers) {
        if (proxyChain == null || proxyChain.isEmpty()) {
            return null;
        }

        List<String> normalizedChain = proxyChain.stream()
                .map(IpAddressUtils::normalizeIp)
                .filter(candidate -> candidate != null)
                .toList();
        if (normalizedChain.isEmpty()) {
            return null;
        }

        String fallbackUntrustedIp = null;
        for (int i = normalizedChain.size() - 1; i >= 0; i--) {
            String candidate = normalizedChain.get(i);
            if (isTrustedProxy(candidate, trustedProxyMatchers)) {
                continue;
            }
            if (fallbackUntrustedIp == null) {
                fallbackUntrustedIp = candidate;
            }
            if (isPublicIp(candidate)) {
                return candidate;
            }
        }

        if (fallbackUntrustedIp != null) {
            return fallbackUntrustedIp;
        }

        return normalizedChain.get(0);
    }

    private static String publicClientIpFromProxyHeaders(String forwarded,
                                                         String forwardedFor,
                                                         String realIp,
                                                         List<IpAddressMatcher> trustedProxyMatchers) {
        String candidate = firstPublicUntrustedIp(parseForwardedForHeader(forwardedFor), trustedProxyMatchers);
        if (candidate != null) {
            return candidate;
        }

        candidate = firstPublicUntrustedIp(parseForwardedHeader(forwarded), trustedProxyMatchers);
        if (candidate != null) {
            return candidate;
        }

        String normalizedRealIp = normalizeIp(realIp);
        return isPublicIp(normalizedRealIp) ? normalizedRealIp : null;
    }

    private static String firstPublicUntrustedIp(List<String> proxyChain, List<IpAddressMatcher> trustedProxyMatchers) {
        if (proxyChain == null || proxyChain.isEmpty()) {
            return null;
        }

        List<String> normalizedChain = proxyChain.stream()
                .map(IpAddressUtils::normalizeIp)
                .filter(candidate -> candidate != null)
                .toList();

        for (int i = normalizedChain.size() - 1; i >= 0; i--) {
            String candidate = normalizedChain.get(i);
            if (isTrustedProxy(candidate, trustedProxyMatchers)) {
                continue;
            }
            if (isPublicIp(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static List<String> parseForwardedHeader(String forwarded) {
        if (forwarded == null || forwarded.isBlank()) {
            return List.of();
        }

        return Arrays.stream(forwarded.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(IpAddressUtils::extractForwardedForValue)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private static String extractForwardedForValue(String forwardedElement) {
        for (String rawPart : forwardedElement.split(";")) {
            String part = rawPart.trim();
            int separator = part.indexOf('=');
            if (separator <= 0) {
                continue;
            }

            String name = part.substring(0, separator).trim();
            if (!"for".equalsIgnoreCase(name)) {
                continue;
            }

            return part.substring(separator + 1).trim();
        }
        return null;
    }

    private static List<String> parseForwardedForHeader(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return List.of();
        }

        return Arrays.stream(forwardedFor.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
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
