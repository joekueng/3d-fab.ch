package com.printcalculator.service;

import com.printcalculator.security.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LegalConsentRequestService {
    private final boolean trustProxyHeaders;
    private final List<IpAddressMatcher> trustedProxies;

    public LegalConsentRequestService(
            @Value("${app.legal-consent.trust-proxy-headers:false}") boolean trustProxyHeaders,
            @Value("${app.legal-consent.trusted-proxy-networks:}") String trustedProxyNetworks) {
        this.trustProxyHeaders = trustProxyHeaders;
        this.trustedProxies = IpAddressUtils.parseTrustedProxyMatchers(trustedProxyNetworks);
    }

    public String resolveClientIp(HttpServletRequest request) {
        String remoteIp = IpAddressUtils.normalizeIp(request.getRemoteAddr());
        // Audit records accept forwarded headers only from explicitly trusted peers.
        boolean trustedPeer = trustProxyHeaders && IpAddressUtils.isTrustedProxy(remoteIp, trustedProxies);
        return IpAddressUtils.normalizeIp(IpAddressUtils.resolveClientIp(
                request.getHeader("Forwarded"), request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"), remoteIp, trustedPeer, trustedProxies));
    }
}
