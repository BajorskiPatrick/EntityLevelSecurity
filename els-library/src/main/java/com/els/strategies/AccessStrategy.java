package com.els.strategies;

import java.util.List;
import java.util.Set;

/**
 * Strategy interface for defining access control behavior.
 * Each implementation encapsulates the complete logic for its access type:
 * <ul>
 * <li>How to generate filter conditions from permission IDs</li>
 * <li>Which Hibernate filter to activate for SELECT queries</li>
 * <li>How to check row-level access for UPDATE/DELETE operations</li>
 * </ul>
 */
public interface AccessStrategy {

    /**
     * Generates a filter condition from the given row IDs.
     * Handles wildcard (*) interpretation specific to this strategy.
     *
     * @param rowIds list of permitted/denied IDs (may contain "*" for wildcard)
     * @return FilterCondition with operator and resolved ID list
     */
    FilterCondition generateCondition(List<Object> rowIds);

    /**
     * Returns the Hibernate filter name used for SELECT operations.
     * Each strategy activates a different database filter.
     *
     * @return Hibernate filter name (e.g. "elsFilter", "elsBlacklistFilter")
     */
    String getFilterName();

    /**
     * Checks whether a specific row ID is accessible under this strategy's rules.
     * Used for row-level validation in UPDATE/DELETE operations.
     *
     * @param targetId     the ID of the row being accessed
     * @param permittedIds the set of IDs from the resolved permission
     * @return true if the operation is allowed on this row
     */
    boolean isIdPermitted(Long targetId, Set<Long> permittedIds);

    /**
     * DTO carrying the resolved filter condition:
     * operator (IN, NOT IN, ALL, NONE) and associated IDs.
     */
    record FilterCondition(String operator, List<Object> ids) {
    }
}
