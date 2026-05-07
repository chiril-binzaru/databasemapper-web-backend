package com.databasemapper.model.enums;

public enum ConditionOperator {
    EQ("="),
    NEQ("<>"),
    GT(">"),
    LT("<"),
    GTE(">="),
    LTE("<="),
    IN("IN"),
    NOT_IN("NOT IN");

    private final String sql;

    ConditionOperator(String sql) {
        this.sql = sql;
    }

    public String toSql() {
        return sql;
    }
}
