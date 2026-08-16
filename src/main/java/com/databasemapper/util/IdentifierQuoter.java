package com.databasemapper.util;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Quotes SQL identifiers the way the target server expects.
 * <p>
 * The quote character is taken from the driver rather than from the stored {@code DatabaseType},
 * because it is a property of the session and not only of the vendor: MySQL quotes with a backtick by
 * default but with a double quote under {@code sql_mode=ANSI_QUOTES}, and the driver reports whichever
 * is in force.
 */
public record IdentifierQuoter(String quoteString) {

    /** The SQL standard quote, and the fallback for a driver that reports none. */
    public static final IdentifierQuoter ANSI = new IdentifierQuoter("\"");

    public IdentifierQuoter {
        if (quoteString == null || quoteString.isBlank()) {
            throw new IllegalArgumentException("Identifier quote string must not be blank");
        }
    }

    /**
     * JDBC returns a single space from {@code getIdentifierQuoteString()} when the driver does not
     * support quoted identifiers; every driver this application ships reports a real quote, so that
     * answer falls back to the standard one rather than emitting bare identifiers, which would break
     * on any name that collides with a reserved word.
     */
    public static IdentifierQuoter forConnection(Connection conn) throws SQLException {
        String quoteString = conn.getMetaData().getIdentifierQuoteString();
        return quoteString == null || quoteString.isBlank() ? ANSI : new IdentifierQuoter(quoteString);
    }

    /**
     * Identifiers reach here from user-supplied mapping JSON, so an embedded quote is doubled rather
     * than passed through. Doubling is how all four supported vendors escape their quote character.
     */
    public String quote(String identifier) {
        return quoteString + identifier.replace(quoteString, quoteString + quoteString) + quoteString;
    }
}
