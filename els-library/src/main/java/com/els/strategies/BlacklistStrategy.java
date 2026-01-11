package com.els.strategies;

import java.util.List;

public class BlacklistStrategy implements AccessStrategy {

    @Override
    public FilterCondition generateCondition(List<Object> rowIds) {
        // Blacklist means "ID NOT IN (...)"
        return new FilterCondition("NOT IN", rowIds);
    }
}
