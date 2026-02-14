package com.els.strategies;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Blacklist strategy: denies access to rows whose IDs are in the permission
 * set.
 *
 * - Empty IDs list → allow access to ALL rows
 * - Specific IDs → allow access to all rows EXCEPT those in the ID list
 * - Uses Hibernate filter "elsBlacklistFilter" (WHERE id NOT IN :ids)
 * - Row check: targetId must NOT be contained in the denied set
 */
@Component
public class BlacklistStrategy implements AccessStrategy {

    @Override
    public FilterCondition generateCondition(List<Long> rowIds) {
        if (rowIds == null || rowIds.isEmpty()) {
            return new FilterCondition("ALL", List.of());
        }
        return new FilterCondition("NOT IN", rowIds);
    }

    @Override
    public String getFilterName() {
        return "elsBlacklistFilter";
    }

    @Override
    public boolean isIdPermitted(Long targetId, Set<Long> permittedIds) {
        return !permittedIds.contains(targetId);
    }
}
