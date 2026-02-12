package com.els.context;

import com.els.domain.User;
import org.springframework.stereotype.Component;

@Component
public class SecurityContext {

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
