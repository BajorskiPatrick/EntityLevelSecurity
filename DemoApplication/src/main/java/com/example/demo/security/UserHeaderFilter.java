package com.example.demo.security;

import com.example.demo.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class UserHeaderFilter implements Filter {

    private final UserRepository userRepository;

    public UserHeaderFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        // Pomijamy sprawdzanie dla endpointów publicznych (login)
        if (path.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        String userIdStr = httpRequest.getHeader("X-User-Id");

        // Jeśli brak nagłówka -> Anonymous
        if (userIdStr == null || userIdStr.isEmpty()) {
            UserContext.setUserId("anonymous");
            chain.doFilter(request, response);
            UserContext.clear();
            return;
        }

        // Walidacja: Czy taki ID istnieje w bazie?
        try {
            Long userId = Long.parseLong(userIdStr);
            if (userRepository.existsById(userId)) {
                UserContext.setUserId(userIdStr);
                chain.doFilter(request, response);
            } else {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid User ID");
            }
        } catch (NumberFormatException e) {
            httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "User ID must be a number");
        } finally {
            UserContext.clear();
        }
    }
}