package io.everyonecodes.deliciousnessness.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.io.IOException;

// What a visitor who is not logged in gets. A browser sees the "private website" page, which
// says they have no access and does not show anything about the site. Other clients get a plain 401.
public class PrivateSiteEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint unauthorized = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, jakarta.servlet.ServletException {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && accept.contains("text/html")) {
            response.sendRedirect(request.getContextPath() + "/private");
        } else {
            unauthorized.commence(request, response, authException);
        }
    }
}
