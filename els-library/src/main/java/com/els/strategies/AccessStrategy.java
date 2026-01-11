package com.els.strategies;

import java.util.List;

public interface AccessStrategy {

    /**
     * returns the SQL/HQL fragment or Specification logic for this strategy
     */
    // For simplicity in this logical phase, let's assume we return a predicate
    // string structure
    // or we might pass a builder. Let's return a "SecurityClause" object or
    // similar.
    // Spec says: "query is modified... SELECT * FROM users WHERE id IN ...".

    // We will return a FilterCondition object that runtime can use.
    FilterCondition generateCondition(List<Object> rowIds);

    // simple DTO for carrying the condition
    record FilterCondition(String operator, List<Object> ids) {
    }
}
