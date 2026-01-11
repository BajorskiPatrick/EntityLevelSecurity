package com.els.cache;

import com.els.strategies.AccessStrategy.FilterCondition;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PermissionCache {

    // Flyweight storage: Key -> Resolved Filter Condition (which contains the ID
    // list)
    // We cache the RESULT of the resolution (the Condition to apply).
    private final Map<PermissionKey, FilterCondition> cache = new ConcurrentHashMap<>();

    public FilterCondition get(PermissionKey key) {
        return cache.get(key);
    }

    public void put(PermissionKey key, FilterCondition value) {
        cache.put(key, value);
    }

    public void invalidate(PermissionKey key) {
        cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    public boolean contains(PermissionKey key) {
        return cache.containsKey(key);
    }
}
