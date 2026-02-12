package com.els.demo.web;

import com.els.context.SecurityContext;
import com.els.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
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

        Long userId = (Long) req.getSession().getAttribute("USER_ID");

        if (userId != null) {
            userRepository.findById(userId).ifPresent(securityContext::setCurrentUser);
        } else {
            String username = req.getHeader("X-User");
            if (username != null) {
                userRepository.findByUsername(username).ifPresent(securityContext::setCurrentUser);
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            securityContext.clear();
        }
    }
}
