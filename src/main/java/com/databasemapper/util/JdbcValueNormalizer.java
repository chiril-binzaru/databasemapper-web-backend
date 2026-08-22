package com.databasemapper.util;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Resolves the ambiguity a JDBC driver introduces when it maps a date-only column onto a class that
 * also carries a time.
 * <p>
 * Oracle returns a {@code DATE} column as {@link Timestamp} so that the time component it is allowed
 * to hold survives the round trip. Serializing such a value through {@link Timestamp#toInstant()}
 * reads it as a local wall-clock reading and re-expresses it in UTC, which moves a midnight value
 * onto the previous day east of Greenwich. The shift follows the DST state of the stored date rather
 * than of today, so it is not a constant offset a consumer could compensate for.
 * <p>
 * The Java class alone cannot distinguish the two meanings, so the decision is taken from
 * {@code ResultSetMetaData.getColumnTypeName()} instead. The column type <em>code</em> is not enough:
 * PostgreSQL reports both {@code timestamp} and {@code timestamptz} as {@link java.sql.Types#TIMESTAMP},
 * and those two need opposite conversions.
 */
public final class JdbcValueNormalizer {

    /**
     * Oracle's date-only type, which the driver widens to {@link Timestamp} under its default
     * {@code mapDateToTimestamp=true}. No other supported vendor reports this name for a value that
     * arrives as a {@link Timestamp} — PostgreSQL, MySQL and SQL Server all hand a date-only column
     * back as {@link java.sql.Date}, which needs no correction.
     */
    private static final String ORACLE_DATE = "DATE";

    private JdbcValueNormalizer() {
    }

    /**
     * Converts a raw JDBC value into the type that reflects what the column actually means. Values
     * this method has no opinion about are returned unchanged, so it is safe to route every column
     * through it.
     *
     * @param value          the value from {@code ResultSet.getObject}, possibly {@code null}
     * @param columnTypeName the vendor's name for the column type, possibly {@code null}
     */
    public static Object normalize(Object value, String columnTypeName) {
        if (value instanceof Timestamp ts && ORACLE_DATE.equalsIgnoreCase(columnTypeName)) {
            return dateOnly(ts);
        }
        return value;
    }

    /**
     * Reads the timestamp as the wall-clock value it was stored as, with no zone conversion. A value
     * at exactly midnight is narrowed to a plain date, because that is what such a column is being
     * used to express; anything else keeps its time rather than being silently truncated.
     */
    private static Object dateOnly(Timestamp ts) {
        LocalDateTime local = ts.toLocalDateTime();
        return local.toLocalTime().equals(LocalTime.MIDNIGHT) ? local.toLocalDate() : local;
    }
}
