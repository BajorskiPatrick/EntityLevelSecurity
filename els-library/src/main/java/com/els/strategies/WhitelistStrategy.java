package com.els.strategies;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Whitelist strategy: grants access to rows whose IDs are IN the permission
 * set.
 * <ul>
 * <li>Wildcard (*) → allow access to ALL rows</li>
 * <li>Specific IDs → allow access only to rows IN the ID list</li>
 * <li>Uses Hibernate filter "elsFilter" (WHERE id IN :ids)</li>
 * <li>Row check: targetId must be contained in the permitted set</li>
 * </ul>
 */
@Component
public class WhitelistStrategy implements AccessStrategy {

    @Override
    public FilterCondition generateCondition(List<Object> rowIds) {
        if (rowIds.stream().anyMatch("*"::equals)) {
            return new FilterCondition("ALL", List.of());
        }
        return new FilterCondition("IN", rowIds);
    }

    @Override
    public String getFilterName() {
        return "elsFilter";
    }

    @Override
    public boolean isIdPermitted(Long targetId, Set<Long> permittedIds) {
        // Whitelist: the target must be IN the allowed set
        return permittedIds.contains(targetId);
    }
}
