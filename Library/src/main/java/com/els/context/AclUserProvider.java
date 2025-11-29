package com.els.context;

@FunctionalInterface
public interface AclUserProvider {
    String getCurrentUserId();
}