package com.databasemapper.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IdentifierQuoter}. The quote character is read from the driver, so the only
 * JDBC involved is a mocked {@link Connection} plus its {@link DatabaseMetaData} - no database is
 * contacted.
 */
@ExtendWith(MockitoExtension.class)
class IdentifierQuoterTest {

    private static final IdentifierQuoter BACKTICK = new IdentifierQuoter("`");

    @Mock
    private Connection conn;

    @Mock
    private DatabaseMetaData metaData;

    // --- A. quote: wrapping -----------------------------------------------------------------------

    @Test
    void ansiQuoterWrapsIdentifierInDoubleQuotes() {
        assertEquals("\"vets\"", IdentifierQuoter.ANSI.quote("vets"));
    }

    @Test
    void backtickQuoterWrapsIdentifierInBackticks() {
        assertEquals("`vets`", BACKTICK.quote("vets"));
    }

    @Test
    void ansiConstantCarriesTheDoubleQuoteAsItsQuoteString() {
        assertEquals("\"", IdentifierQuoter.ANSI.quoteString());
    }

    // --- B. quote: escaping (the injection guard) -------------------------------------------------

    @Test
    void ansiQuoterDoublesAnEmbeddedDoubleQuote() {
        assertEquals("\"we\"\"ird\"", IdentifierQuoter.ANSI.quote("we\"ird"));
    }

    @Test
    void backtickQuoterDoublesAnEmbeddedBacktick() {
        assertEquals("`we``ird`", BACKTICK.quote("we`ird"));
    }

    @Test
    void ansiQuoterLeavesAnEmbeddedBacktickUntouched() {
        assertEquals("\"we`ird\"", IdentifierQuoter.ANSI.quote("we`ird"));
    }

    @Test
    void backtickQuoterLeavesAnEmbeddedDoubleQuoteUntouched() {
        assertEquals("`we\"ird`", BACKTICK.quote("we\"ird"));
    }

    @Test
    void ansiQuoterDoublesEveryOccurrenceOfAnEmbeddedDoubleQuote() {
        assertEquals("\"a\"\"b\"\"c\"", IdentifierQuoter.ANSI.quote("a\"b\"c"));
    }

    /** A terminating quote is the classic break-out payload, so it is pinned explicitly. */
    @Test
    void ansiQuoterNeutralisesAnIdentifierThatTriesToCloseTheQuoteAndAppendSql() {
        assertEquals("\"x\"\"; DROP TABLE vets; --\"", IdentifierQuoter.ANSI.quote("x\"; DROP TABLE vets; --"));
    }

    // --- C. forConnection -------------------------------------------------------------------------

    @Test
    void forConnectionReturnsBacktickQuoterWhenDriverReportsBacktick() throws SQLException {
        stubIdentifierQuoteString("`");

        assertEquals(BACKTICK, IdentifierQuoter.forConnection(conn));
    }

    @Test
    void forConnectionReturnsDoubleQuoteQuoterWhenDriverReportsDoubleQuote() throws SQLException {
        stubIdentifierQuoteString("\"");

        assertEquals(IdentifierQuoter.ANSI, IdentifierQuoter.forConnection(conn));
    }

    @Test
    void forConnectionFallsBackToAnsiWhenDriverReportsTheSingleSpaceMeaningNoQuotingSupport() throws SQLException {
        stubIdentifierQuoteString(" ");

        assertSame(IdentifierQuoter.ANSI, IdentifierQuoter.forConnection(conn));
    }

    @Test
    void forConnectionFallsBackToAnsiWhenDriverReportsAnEmptyString() throws SQLException {
        stubIdentifierQuoteString("");

        assertSame(IdentifierQuoter.ANSI, IdentifierQuoter.forConnection(conn));
    }

    @Test
    void forConnectionFallsBackToAnsiWhenDriverReportsNull() throws SQLException {
        stubIdentifierQuoteString(null);

        assertSame(IdentifierQuoter.ANSI, IdentifierQuoter.forConnection(conn));
    }

    @Test
    void forConnectionPropagatesSqlExceptionFromTheDriver() throws SQLException {
        when(conn.getMetaData()).thenThrow(new SQLException("connection closed"));

        SQLException ex = assertThrows(SQLException.class, () -> IdentifierQuoter.forConnection(conn));

        assertEquals("connection closed", ex.getMessage());
    }

    // --- D. constructor guard ---------------------------------------------------------------------

    @Test
    void constructorRejectsNullQuoteString() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new IdentifierQuoter(null));

        assertEquals("Identifier quote string must not be blank", ex.getMessage());
    }

    @Test
    void constructorRejectsEmptyQuoteString() {
        assertThrows(IllegalArgumentException.class, () -> new IdentifierQuoter(""));
    }

    @Test
    void constructorRejectsBlankQuoteString() {
        assertThrows(IllegalArgumentException.class, () -> new IdentifierQuoter("   "));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private void stubIdentifierQuoteString(String quoteString) throws SQLException {
        when(conn.getMetaData()).thenReturn(metaData);
        when(metaData.getIdentifierQuoteString()).thenReturn(quoteString);
    }
}
