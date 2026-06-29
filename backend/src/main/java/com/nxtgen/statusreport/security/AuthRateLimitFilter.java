package com.nxtgen.statusreport.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory rate limiter for the unauthenticated auth endpoints
 * (login / register) to blunt brute-force and credential-stuffing attempts.
 * Fixed window of {@link #MAX_ATTEMPTS} requests per {@link #WINDOW_SECONDS}
 * per client IP; over the limit returns HTTP 429.
 *
 * <p>Good enough for a single backend instance. For a multi-node deployment,
 * move the counters to a shared store (e.g. Redis) so the limit is global.
 */
@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_SECONDS = 60;
    private static final int MAX_TRACKED_IPS = 50_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only throttle the sensitive auth POSTs; everything else passes through.
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !(uri.endsWith("/api/auth/login") || uri.endsWith("/api/auth/register"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isLimited(clientIp(request))) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many attempts. Please wait a minute and try again.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isLimited(String ip) {
        long now = Instant.now().getEpochSecond();
        if (windows.size() > MAX_TRACKED_IPS) {
            windows.clear(); // crude overflow guard against memory growth from spoofed IPs
        }
        Window window = windows.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.start >= WINDOW_SECONDS) {
                return new Window(now);
            }
            existing.count++;
            return existing;
        });
        return window.count > MAX_ATTEMPTS;
    }

    /** Behind nginx the real client is the first hop of X-Forwarded-For. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
            this.count = 1;
        }
    }
}
