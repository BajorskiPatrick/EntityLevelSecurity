package com.els.context;

import com.els.domain.User;
import org.springframework.stereotype.Component;

@Component
public class SecurityContext {

    // In a real Spring Security app, we'd wrap SecurityContextHolder.
    // Here, for this custom implementation, we use a simple ThreadLocal.
    private static final ThreadLocal<User> currentUser = new ThreadLocal<>();

    public void setCurrentUser(User user) {
        currentUser.set(user);
    }

    public User getCurrentUser() {
        return currentUser.get();
    }

    public void clear() {
        currentUser.remove();
    }
}
