package com.databasemapper.util;

public record ColumnPath(String schema, String table, String column) {

    public static ColumnPath parse(String path) {
        String[] parts = path.split("\\.", 3);
        return switch (parts.length) {
            case 3 -> new ColumnPath(parts[0], parts[1], parts[2]);
            case 2 -> new ColumnPath(null, parts[0], parts[1]);
            default -> throw new IllegalArgumentException("Invalid column path: " + path);
        };
    }

    public String tableKey() {
        return schema != null ? schema + "." + table : table;
    }

    // Unique alias safe to use as a ResultSet column label
    public String columnAlias() {
        String prefix = schema != null ? schema + "__" + table : table;
        return prefix + "__" + column;
    }

    public String quotedFull() {
        if (schema != null) {
            return "\"" + schema + "\".\"" + table + "\".\"" + column + "\"";
        }
        return "\"" + table + "\".\"" + column + "\"";
    }

    public String quotedTableRef() {
        if (schema != null) return "\"" + schema + "\".\"" + table + "\"";
        return "\"" + table + "\"";
    }
}
