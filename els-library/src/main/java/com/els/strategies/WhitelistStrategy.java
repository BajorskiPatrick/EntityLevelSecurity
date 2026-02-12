package com.els.strategies;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Whitelist strategy: grants access to rows whose IDs are IN the permission
 * set.
 *
 * - Wildcard (*) → allow access to ALL rows
 * - Specific IDs → allow access only to rows IN the ID list
 * - Uses Hibernate filter "elsFilter" (WHERE id IN :ids)
 * - Row check: targetId must be contained in the permitted set
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
        return permittedIds.contains(targetId);
    }
}
