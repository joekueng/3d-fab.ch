package com.printcalculator.security;

import com.printcalculator.config.AllowedOriginService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Component
public class AdminCsrfProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final AllowedOriginService allowedOriginService;

    public AdminCsrfProtectionFilter(AllowedOriginService allowedOriginService) {
        this.allowedOriginService = allowedOriginService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = resolvePath(request);
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        return !path.startsWith("/api/admin/") || SAFE_METHODS.contains(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String referer = request.getHeader(HttpHeaders.REFERER);

        if (allowedOriginService.isAllowed(origin) || allowedOriginService.isAllowed(referer)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"CSRF_INVALID\"}");
    }

    private String resolvePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }
}
