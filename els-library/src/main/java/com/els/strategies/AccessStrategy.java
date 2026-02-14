package com.els.strategies;

import java.util.List;
import java.util.Set;

public interface AccessStrategy {

    FilterCondition generateCondition(List<Long> rowIds);

    String getFilterName();

    boolean isIdPermitted(Long targetId, Set<Long> permittedIds);

    /**
     * DTO carrying the resolved filter condition:
     * operator (IN, NOT IN, ALL, NONE) and associated IDs.
     */
    record FilterCondition(String operator, List<Long> ids) {
    }
}
