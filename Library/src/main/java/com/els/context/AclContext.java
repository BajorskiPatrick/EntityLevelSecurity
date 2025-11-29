package com.els.context;

public class AclContext {
    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);

    public static void setCurrentUser(String userId) {
        currentUser.set(userId);
        active.set(true);
    }

    public static String getCurrentUser() {
        return currentUser.get();
    }

    public static boolean isActive() {
        return active.get();
    }

    public static void clear() {
        currentUser.remove();
        active.remove();
    }
}