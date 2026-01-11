package com.els.cache;

import com.els.domain.Action;

public record PermissionKey(String username, String entityName, Action action) {
}
