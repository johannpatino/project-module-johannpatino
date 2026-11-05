package io.everyonecodes.deliciousnessness.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

// Stops password guessing, for the login form and for HTTP Basic alike. Three limits apply:
//  - 8 wrong passwords in 10 minutes for one username from one address, so one stranger cannot
//    lock the owner out for everyone;
//  - 60 wrong passwords an hour for one username from anywhere, which stops guessing spread over
//    many addresses;
//  - 30 wrong passwords an hour from one address for any usernames, which blocks that address from
//    logging in for the rest of the hour. This is not cleared by a correct login, so a guesser cannot
//    reset it by logging in to their own account in between.
// Any of them gives "429 Too Many Requests" until it passes. For a permanent block of an address,
// use a rule in Cloudflare (Security > WAF), which stops it before it reaches the app.
@Component
public class LoginThrottle extends OncePerRequestFilter {

    private static final Duration SHORT_WINDOW = Duration.ofMinutes(10);

    private final AttemptLimiter perUserAndAddress = new AttemptLimiter(8, SHORT_WINDOW);
    private final AttemptLimiter perUser = new AttemptLimiter(60, Duration.ofHours(1));
    private final AttemptLimiter perAddress = new AttemptLimiter(30, Duration.ofHours(1));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String username = attemptedUsername(request);
        if (username != null && (perAddress.isLimited(clientAddress(request))
                || perUserAndAddress.isLimited(username + "|" + clientAddress(request))
                || perUser.isLimited(username))) {
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(SHORT_WINDOW.toSeconds()));
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Too many failed logins. Try again in a few minutes.");
            return;
        }
        chain.doFilter(request, response);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = normalize(event.getAuthentication().getName());
        if (username != null && event.getException() instanceof BadCredentialsException) {
            perUserAndAddress.record(username + "|" + currentClientAddress());
            perUser.record(username);
            perAddress.record(currentClientAddress());
        }
    }

    // For tests, which all connect from the same address.
    public void reset() {
        perUserAndAddress.clearAll();
        perUser.clearAll();
        perAddress.clearAll();
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = normalize(event.getAuthentication().getName());
        if (username != null) {
            perUserAndAddress.clear(username + "|" + currentClientAddress());
        }
    }

    // Behind Cloudflare the real visitor address is in CF-Connecting-IP. Without it, the connection's own
    // address is used. A made-up header only gets around the first limit, never the hourly one.
    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("CF-Connecting-IP");
        return forwarded != null && !forwarded.isBlank() ? forwarded.trim() : request.getRemoteAddr();
    }

    // The events fire on the request's own thread, so the request is still at hand.
    private static String currentClientAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return clientAddress(attributes.getRequest());
        }
        return "unknown";
    }

    private static String attemptedUsername(HttpServletRequest request) {
        if ("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath())) {
            return normalize(request.getParameter("username"));
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()),
                        StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                return normalize(colon < 0 ? decoded : decoded.substring(0, colon));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private static String normalize(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }
}
