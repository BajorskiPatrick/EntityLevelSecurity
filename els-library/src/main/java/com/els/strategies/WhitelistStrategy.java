package com.els.strategies;

import java.util.List;

public class WhitelistStrategy implements AccessStrategy {

    @Override
    public FilterCondition generateCondition(List<Object> rowIds) {
        // Whitelist means "ID IN (...)"
        return new FilterCondition("IN", rowIds);
    }
}
