package com.databasemapper.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcValueNormalizerTest {

    @Test
    @DisplayName("An Oracle DATE at midnight becomes a plain date, not the previous day")
    void oracleDateAtMidnightNarrowsToLocalDate() {
        Timestamp ts = Timestamp.valueOf("2010-03-04 00:00:00");

        Object normalized = JdbcValueNormalizer.normalize(ts, "DATE");

        assertThat(normalized).isEqualTo(LocalDate.of(2010, 3, 4));
    }

    @Test
    @DisplayName("An Oracle DATE carrying a time keeps it rather than being truncated")
    void oracleDateWithTimeKeepsTheTime() {
        Timestamp ts = Timestamp.valueOf("2010-03-04 14:30:15");

        Object normalized = JdbcValueNormalizer.normalize(ts, "DATE");

        assertThat(normalized).isEqualTo(LocalDateTime.of(2010, 3, 4, 14, 30, 15));
    }

    @Test
    @DisplayName("A sub-second time component still counts as a time")
    void oracleDateWithNanosIsNotMidnight() {
        Timestamp ts = Timestamp.valueOf("2010-03-04 00:00:00.001");

        Object normalized = JdbcValueNormalizer.normalize(ts, "DATE");

        assertThat(normalized).isEqualTo(LocalDateTime.of(2010, 3, 4, 0, 0, 0, 1_000_000));
    }

    @Test
    @DisplayName("The column type name is matched without regard to case")
    void typeNameMatchIsCaseInsensitive() {
        Timestamp ts = Timestamp.valueOf("2010-03-04 00:00:00");

        assertThat(JdbcValueNormalizer.normalize(ts, "date")).isEqualTo(LocalDate.of(2010, 3, 4));
        assertThat(JdbcValueNormalizer.normalize(ts, "Date")).isEqualTo(LocalDate.of(2010, 3, 4));
    }

    /**
     * The regression this whole class exists to prevent. PostgreSQL reports {@code timestamptz} with
     * the same type code as {@code timestamp}, and its value is a genuine instant: converting it as a
     * wall-clock reading would shift it in the opposite direction. Only {@code DATE} may be rewritten.
     */
    @ParameterizedTest
    @ValueSource(strings = {"timestamp", "timestamptz", "TIMESTAMP", "TIMESTAMP WITH TIME ZONE",
            "datetime", "datetime2", "smalldatetime", "datetimeoffset"})
    @DisplayName("Every timestamp type other than DATE is left for the existing instant conversion")
    void nonDateTimestampTypesArePassedThroughUntouched(String columnTypeName) {
        Timestamp ts = Timestamp.valueOf("2010-03-04 00:00:00");

        Object normalized = JdbcValueNormalizer.normalize(ts, columnTypeName);

        assertThat(normalized).isSameAs(ts);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    @DisplayName("A missing or blank type name leaves the value alone")
    void absentTypeNameIsNotTreatedAsDate(String columnTypeName) {
        Timestamp ts = Timestamp.valueOf("2010-03-04 00:00:00");

        assertThat(JdbcValueNormalizer.normalize(ts, columnTypeName)).isSameAs(ts);
    }

    @Test
    @DisplayName("A java.sql.Date is already unambiguous and is not rewritten")
    void sqlDateIsPassedThrough() {
        java.sql.Date date = java.sql.Date.valueOf("2010-03-04");

        assertThat(JdbcValueNormalizer.normalize(date, "DATE")).isSameAs(date);
    }

    @Test
    @DisplayName("Non-temporal values are returned unchanged even under a DATE type name")
    void nonTemporalValuesArePassedThrough() {
        BigDecimal number = new BigDecimal("42");

        assertThat(JdbcValueNormalizer.normalize(number, "DATE")).isSameAs(number);
        assertThat(JdbcValueNormalizer.normalize("rabies shot", "VARCHAR2")).isEqualTo("rabies shot");
    }

    @Test
    @DisplayName("A null value survives normalization")
    void nullIsPassedThrough() {
        assertThat(JdbcValueNormalizer.normalize(null, "DATE")).isNull();
    }

    /**
     * Pins the defect itself: whatever else changes, the normalized value must not drift a day when
     * read back as a date. Timestamp.valueOf interprets the literal in the default zone, so this fails
     * under the old toInstant() conversion in any zone east of Greenwich.
     */
    @Test
    @DisplayName("The stored calendar day is preserved rather than shifted by the JVM zone")
    void calendarDayIsPreserved() {
        Timestamp ts = Timestamp.valueOf("2010-03-04 00:00:00");

        assertThat(JdbcValueNormalizer.normalize(ts, "DATE"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LOCAL_DATE)
                .hasToString("2010-03-04");
    }
}
