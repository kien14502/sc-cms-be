package com.vnpt.mac.common.web;

import com.vnpt.mac.security.MacPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Access log for every incoming API call: method, path (+ query string), status, duration,
 * caller identity, and client IP. Wired into the Spring Security filter chain (see
 * SecurityConfig) right after BearerAuthenticationFilter, so the authenticated principal is
 * already resolved and the log line is written before SecurityContextHolderFilter's own
 * request-scoped cleanup runs (that filter wraps the whole chain, so its cleanup only happens
 * after this filter's finally block has already logged).
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("com.vnpt.mac.http");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        MDC.put("requestId", requestId);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            int status = response.getStatus();
            log.atLevel(level(status)).log("{} {}{} {} {}ms user={} ip={} requestId={}",
                    request.getMethod(), request.getRequestURI(), queryString(request),
                    status, durationMs, currentUser(), clientIp(request), requestId);
            MDC.remove("requestId");
        }
    }

    private String requestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        return header == null || header.isBlank() ? UUID.randomUUID().toString() : header;
    }

    private String queryString(HttpServletRequest request) {
        String qs = request.getQueryString();
        return qs == null ? "" : "?" + qs;
    }

    private String currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof MacPrincipal p ? p.email() : "-";
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }

    private Level level(int status) {
        if (status >= 500) return Level.ERROR;
        if (status >= 400) return Level.WARN;
        return Level.INFO;
    }
}
