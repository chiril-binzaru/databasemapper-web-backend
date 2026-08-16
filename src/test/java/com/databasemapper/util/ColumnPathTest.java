package com.databasemapper.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ColumnPath}: parsing of the {@code schema.table.column} strings that arrive
 * from user-supplied mapping JSON, the alias used as the ResultSet lookup key, and the vendor-aware
 * quoting used to build hydration SQL. Pure logic - no JDBC and no database.
 */
class ColumnPathTest {

    private static final IdentifierQuoter BACKTICK = new IdentifierQuoter("`");

    // --- A. parse ---------------------------------------------------------------------------------

    @Test
    void parseSplitsAThreePartPathIntoSchemaTableAndColumn() {
        ColumnPath path = ColumnPath.parse("petclinic.vets.id");

        assertEquals("petclinic", path.schema());
        assertEquals("vets", path.table());
        assertEquals("id", path.column());
    }

    @Test
    void parseLeavesSchemaNullForATwoPartPath() {
        ColumnPath path = ColumnPath.parse("vets.id");

        assertNull(path.schema());
        assertEquals("vets", path.table());
        assertEquals("id", path.column());
    }

    @Test
    void parseRejectsASinglePartPath() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ColumnPath.parse("id"));

        assertTrue(ex.getMessage().contains("id"),
                "message should echo the offending path but was: " + ex.getMessage());
    }

    @Test
    void parseRejectsAnEmptyPath() {
        assertThrows(IllegalArgumentException.class, () -> ColumnPath.parse(""));
    }

    /**
     * Documents the actual behaviour of {@code split("\\.", 3)}: a fourth segment is not rejected,
     * it is absorbed into the column, so {@code a.b.c.d} yields the column {@code c.d}.
     */
    @Test
    void parseAbsorbsAnyFourthSegmentIntoTheColumnRatherThanRejectingIt() {
        ColumnPath path = ColumnPath.parse("db.petclinic.vets.id");

        assertEquals("db", path.schema());
        assertEquals("petclinic", path.table());
        assertEquals("vets.id", path.column());
    }

    // --- B. tableKey ------------------------------------------------------------------------------

    @Test
    void tableKeyJoinsSchemaAndTableForAThreePartPath() {
        assertEquals("petclinic.vets", ColumnPath.parse("petclinic.vets.id").tableKey());
    }

    @Test
    void tableKeyIsTheBareTableForATwoPartPath() {
        assertEquals("vets", ColumnPath.parse("vets.id").tableKey());
    }

    // --- C. columnAlias (the ResultSet lookup key) ------------------------------------------------

    @Test
    void columnAliasUsesDoubleUnderscoresForAThreePartPath() {
        assertEquals("petclinic__vets__id", ColumnPath.parse("petclinic.vets.id").columnAlias());
    }

    @Test
    void columnAliasOmitsTheSchemaSegmentForATwoPartPath() {
        assertEquals("vets__id", ColumnPath.parse("vets.id").columnAlias());
    }

    // --- D. quotedFull ----------------------------------------------------------------------------

    @Test
    void quotedFullUsesDoubleQuotesOnEverySegmentOfAThreePartPath() {
        assertEquals("\"petclinic\".\"vets\".\"id\"",
                ColumnPath.parse("petclinic.vets.id").quotedFull(IdentifierQuoter.ANSI));
    }

    @Test
    void quotedFullUsesBackticksOnEverySegmentOfAThreePartPath() {
        assertEquals("`petclinic`.`vets`.`id`",
                ColumnPath.parse("petclinic.vets.id").quotedFull(BACKTICK));
    }

    @Test
    void quotedFullOmitsTheSchemaSegmentForATwoPartPathWithAnsiQuoting() {
        assertEquals("\"vets\".\"id\"", ColumnPath.parse("vets.id").quotedFull(IdentifierQuoter.ANSI));
    }

    @Test
    void quotedFullOmitsTheSchemaSegmentForATwoPartPathWithBacktickQuoting() {
        assertEquals("`vets`.`id`", ColumnPath.parse("vets.id").quotedFull(BACKTICK));
    }

    // --- E. quotedTableRef ------------------------------------------------------------------------

    @Test
    void quotedTableRefQuotesSchemaAndTableWithAnsiQuoting() {
        assertEquals("\"petclinic\".\"vets\"",
                ColumnPath.parse("petclinic.vets.id").quotedTableRef(IdentifierQuoter.ANSI));
    }

    @Test
    void quotedTableRefQuotesSchemaAndTableWithBacktickQuoting() {
        assertEquals("`petclinic`.`vets`",
                ColumnPath.parse("petclinic.vets.id").quotedTableRef(BACKTICK));
    }

    @Test
    void quotedTableRefIsTheBareQuotedTableForATwoPartPathWithAnsiQuoting() {
        assertEquals("\"vets\"", ColumnPath.parse("vets.id").quotedTableRef(IdentifierQuoter.ANSI));
    }

    @Test
    void quotedTableRefIsTheBareQuotedTableForATwoPartPathWithBacktickQuoting() {
        assertEquals("`vets`", ColumnPath.parse("vets.id").quotedTableRef(BACKTICK));
    }

    // --- F. quoting escapes embedded quote characters ---------------------------------------------

    @Test
    void quotedFullDoublesAnEmbeddedDoubleQuoteInEverySegment() {
        assertEquals("\"pet\"\"clinic\".\"ve\"\"ts\".\"i\"\"d\"",
                ColumnPath.parse("pet\"clinic.ve\"ts.i\"d").quotedFull(IdentifierQuoter.ANSI));
    }

    @Test
    void quotedFullDoublesAnEmbeddedBacktickInEverySegment() {
        assertEquals("`pet``clinic`.`ve``ts`.`i``d`",
                ColumnPath.parse("pet`clinic.ve`ts.i`d").quotedFull(BACKTICK));
    }

    @Test
    void quotedTableRefDoublesAnEmbeddedQuoteCharacter() {
        assertEquals("\"pet\"\"clinic\".\"ve\"\"ts\"",
                ColumnPath.parse("pet\"clinic.ve\"ts.id").quotedTableRef(IdentifierQuoter.ANSI));
    }
}
