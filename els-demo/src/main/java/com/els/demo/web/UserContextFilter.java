package com.els.demo.web;

import com.els.context.SecurityContext;
import com.els.domain.User;
import com.els.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class UserContextFilter implements Filter {

    private final UserRepository userRepository;
    private final SecurityContext securityContext;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String username = req.getHeader("X-User");

        if (username != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                securityContext.setCurrentUser(user);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            securityContext.clear();
        }
    }
}
