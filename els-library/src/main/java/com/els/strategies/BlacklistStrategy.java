package com.els.strategies;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Blacklist strategy: denies access to rows whose IDs are in the permission
 * set.
 * <ul>
 * <li>Wildcard (*) → deny access to ALL rows (complete block)</li>
 * <li>Specific IDs → allow access to all rows EXCEPT those in the ID list</li>
 * <li>Uses Hibernate filter "elsBlacklistFilter" (WHERE id NOT IN :ids)</li>
 * <li>Row check: targetId must NOT be contained in the denied set</li>
 * </ul>
 */
@Component
public class BlacklistStrategy implements AccessStrategy {

    @Override
    public FilterCondition generateCondition(List<Object> rowIds) {
        if (rowIds.stream().anyMatch("*"::equals)) {
            return new FilterCondition("NONE", List.of());
        }
        return new FilterCondition("NOT IN", rowIds);
    }

    @Override
    public String getFilterName() {
        return "elsBlacklistFilter";
    }

    @Override
    public boolean isIdPermitted(Long targetId, Set<Long> permittedIds) {
        // Blacklist: the target must NOT be in the denied set
        return !permittedIds.contains(targetId);
    }
}
