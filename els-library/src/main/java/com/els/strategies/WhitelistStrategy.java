package com.els.strategies;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Whitelist strategy: grants access only to rows whose IDs are IN the
 * permission set.
 *
 * - Empty IDs list → deny access to ALL rows (NONE)
 * - Specific IDs → allow access only to rows IN the ID list
 * - Uses Hibernate filter "elsFilter" (WHERE id IN :ids)
 * - Row check: targetId must be contained in the permitted set
 */
@Component
public class WhitelistStrategy implements AccessStrategy {

    @Override
    public FilterCondition generateCondition(List<Long> rowIds) {
        if (rowIds == null || rowIds.isEmpty()) {
            return new FilterCondition("NONE", List.of());
        }
        return new FilterCondition("IN", rowIds);
    }

    @Override
    public FilterCondition generateInsertCondition(List<Long> rowIds) {
        if (rowIds == null || rowIds.isEmpty()) {
            return new FilterCondition("NONE", List.of());
        }
        return new FilterCondition("ALL", List.of());
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
