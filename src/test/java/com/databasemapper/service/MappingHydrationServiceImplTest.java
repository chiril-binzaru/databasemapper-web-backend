package com.databasemapper.service;

import com.databasemapper.config.JdbcConnectionFactory;
import com.databasemapper.dto.DataFetchRequest;
import com.databasemapper.model.Connection;
import com.databasemapper.model.Database;
import com.databasemapper.model.Endpoint;
import com.databasemapper.model.Mapping;
import com.databasemapper.model.Service;
import com.databasemapper.repository.ConnectionRepository;
import com.databasemapper.repository.EndpointRepository;
import com.databasemapper.repository.MappingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the SQL that {@link MappingHydrationServiceImpl} generates, driven through the
 * public {@code fetchData} entry point with every collaborator mocked. The whole point is the
 * identifier quote character: MySQL parses {@code "} as a string literal, so a hardcoded ANSI quote
 * anywhere in the statement - SELECT list, column alias, JOIN clause or WHERE clause - breaks every
 * hydration call against MySQL. No database is contacted; the SQL is captured from
 * {@code Connection.prepareStatement}.
 */
@ExtendWith(MockitoExtension.class)
class MappingHydrationServiceImplTest {

    private static final Long ENDPOINT_ID = 42L;
    private static final Long DATABASE_ID = 7L;

    private static final String BACKTICK = "`";
    private static final String DOUBLE_QUOTE = "\"";

    /**
     * A vet joined to its specialties through a bridge table, so the generated statement carries a
     * SELECT list with aliases, two JOIN clauses, an additional join condition and a WHERE clause -
     * every site that quotes an identifier.
     */
    private static final String JOINED_MAPPING_JSON = """
            {
              "modelName": "Vet",
              "fieldMappings": [
                { "kind": "VALUE",
                  "serviceInfo": { "modelField": "id" },
                  "databaseInfo": { "columnPath": "petclinic.vets.id", "primaryKey": true } },
                { "kind": "VALUE",
                  "serviceInfo": { "modelField": "specialty" },
                  "databaseInfo": { "columnPath": "petclinic.specialties.name", "primaryKey": false } }
              ],
              "joins": [
                { "left": "petclinic.vets.id", "right": "petclinic.vet_specialties.vet_id" },
                { "left": "petclinic.vet_specialties.specialty_id", "right": "petclinic.specialties.id",
                  "additionalJoinConditions": [
                    { "left": "petclinic.specialties.active", "operator": "EQ", "rightLiteral": "Y" }
                  ] }
              ]
            }
            """;

    /** Single table, no joins - used for the list path and the escaping check. */
    private static final String FLAT_MAPPING_JSON = """
            {
              "modelName": "Vet",
              "fieldMappings": [
                { "kind": "VALUE",
                  "serviceInfo": { "modelField": "id" },
                  "databaseInfo": { "columnPath": "petclinic.vets.id", "primaryKey": true } },
                { "kind": "VALUE",
                  "serviceInfo": { "modelField": "firstName" },
                  "databaseInfo": { "columnPath": "petclinic.vets.first_name", "primaryKey": false } }
              ]
            }
            """;

    /** The column name carries a double quote, as a mapping saved by a user could. */
    private static final String QUOTE_IN_IDENTIFIER_MAPPING_JSON = """
            {
              "modelName": "Vet",
              "fieldMappings": [
                { "kind": "VALUE",
                  "serviceInfo": { "modelField": "id" },
                  "databaseInfo": { "columnPath": "petclinic.vets.we\\"ird", "primaryKey": true } }
              ]
            }
            """;

    /** A visit whose date column is the Oracle {@code DATE} the driver widens to a Timestamp. */
    private static final String VISIT_MAPPING_JSON = """
            {
              "modelName": "Visit",
              "fieldMappings": [
                { "kind": "VALUE",
                  "serviceInfo": { "modelField": "id" },
                  "databaseInfo": { "columnPath": "petclinic.visits.id", "primaryKey": true } },
                { "kind": "VALUE",
                  "serviceInfo": { "modelField": "date" },
                  "databaseInfo": { "columnPath": "petclinic.visits.visit_date" } }
              ]
            }
            """;

    @Mock
    private EndpointRepository endpointRepository;

    @Mock
    private MappingRepository mappingRepository;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private JdbcConnectionFactory jdbcConnectionFactory;

    @Mock
    private java.sql.Connection jdbcConn;

    @Mock
    private DatabaseMetaData jdbcMetaData;

    @Mock
    private PreparedStatement stmt;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MappingHydrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MappingHydrationServiceImpl(
                endpointRepository, mappingRepository, connectionRepository, jdbcConnectionFactory, objectMapper);
    }

    // --- A. the bug: MySQL reports a backtick ------------------------------------------------------

    @Test
    void generatedSqlContainsNoDoubleQuoteAtAllWhenTheDriverReportsABacktick() throws Exception {
        stubRepositories(JOINED_MAPPING_JSON, false);
        stubDriverQuote(BACKTICK);
        stubSingleRow(new String[]{"petclinic__vets__id", "petclinic__specialties__name"},
                new Object[]{1, "surgery"});

        service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of("id", 1)));

        String sql = capturedSql();
        assertFalse(sql.contains(DOUBLE_QUOTE),
                "no ANSI double quote may survive anywhere in the statement but SQL was: " + sql);
    }

    @Test
    void backtickDriverProducesBacktickQuotingInSelectAliasJoinAndWhereClauses() throws Exception {
        stubRepositories(JOINED_MAPPING_JSON, false);
        stubDriverQuote(BACKTICK);
        stubSingleRow(new String[]{"petclinic__vets__id", "petclinic__specialties__name"},
                new Object[]{1, "surgery"});

        service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of("id", 1)));

        assertEquals(
                "SELECT `petclinic`.`vets`.`id` AS `petclinic__vets__id`, "
                + "`petclinic`.`specialties`.`name` AS `petclinic__specialties__name` "
                + "FROM `petclinic`.`vets` "
                + "JOIN `petclinic`.`vet_specialties` "
                + "ON `petclinic`.`vets`.`id` = `petclinic`.`vet_specialties`.`vet_id` "
                + "JOIN `petclinic`.`specialties` "
                + "ON `petclinic`.`vet_specialties`.`specialty_id` = `petclinic`.`specialties`.`id` "
                + "AND `petclinic`.`specialties`.`active` = ? "
                + "WHERE `petclinic`.`vets`.`id` = ?",
                capturedSql());
    }

    /** The alias was quoted by a separate hardcoded literal, so it is pinned on its own. */
    @Test
    void backtickDriverQuotesTheColumnAliasWithABacktickToo() throws Exception {
        stubRepositories(JOINED_MAPPING_JSON, false);
        stubDriverQuote(BACKTICK);
        stubSingleRow(new String[]{"petclinic__vets__id", "petclinic__specialties__name"},
                new Object[]{1, "surgery"});

        service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of("id", 1)));

        assertTrue(capturedSql().contains("AS `petclinic__vets__id`"),
                "alias must use the driver quote but SQL was: " + capturedSql());
    }

    // --- B. no behaviour change for the ANSI vendors ----------------------------------------------

    @Test
    void doubleQuoteDriverProducesTheUnchangedAnsiStatement() throws Exception {
        stubRepositories(JOINED_MAPPING_JSON, false);
        stubDriverQuote(DOUBLE_QUOTE);
        stubSingleRow(new String[]{"petclinic__vets__id", "petclinic__specialties__name"},
                new Object[]{1, "surgery"});

        service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of("id", 1)));

        assertEquals(
                "SELECT \"petclinic\".\"vets\".\"id\" AS \"petclinic__vets__id\", "
                + "\"petclinic\".\"specialties\".\"name\" AS \"petclinic__specialties__name\" "
                + "FROM \"petclinic\".\"vets\" "
                + "JOIN \"petclinic\".\"vet_specialties\" "
                + "ON \"petclinic\".\"vets\".\"id\" = \"petclinic\".\"vet_specialties\".\"vet_id\" "
                + "JOIN \"petclinic\".\"specialties\" "
                + "ON \"petclinic\".\"vet_specialties\".\"specialty_id\" = \"petclinic\".\"specialties\".\"id\" "
                + "AND \"petclinic\".\"specialties\".\"active\" = ? "
                + "WHERE \"petclinic\".\"vets\".\"id\" = ?",
                capturedSql());
    }

    @Test
    void driverReportingNoQuotingSupportFallsBackToAnsiQuoting() throws Exception {
        stubRepositories(JOINED_MAPPING_JSON, false);
        stubDriverQuote(" ");
        stubSingleRow(new String[]{"petclinic__vets__id", "petclinic__specialties__name"},
                new Object[]{1, "surgery"});

        service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of("id", 1)));

        assertTrue(capturedSql().startsWith("SELECT \"petclinic\".\"vets\".\"id\""),
                "should fall back to the standard quote but SQL was: " + capturedSql());
    }

    // --- C. parameters are unaffected by the quoting change ---------------------------------------

    @Test
    void joinConditionLiteralIsBoundBeforeTheFilterValue() throws Exception {
        stubRepositories(JOINED_MAPPING_JSON, false);
        stubDriverQuote(BACKTICK);
        stubSingleRow(new String[]{"petclinic__vets__id", "petclinic__specialties__name"},
                new Object[]{1, "surgery"});

        service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of("id", 1)));

        verify(stmt).setObject(1, "Y");
        verify(stmt).setObject(2, 1);
    }

    @Test
    void hydratedNodeCarriesTheValuesReadUnderTheGeneratedAliases() throws Exception {
        stubRepositories(JOINED_MAPPING_JSON, false);
        stubDriverQuote(BACKTICK);
        stubSingleRow(new String[]{"petclinic__vets__id", "petclinic__specialties__name"},
                new Object[]{1, "surgery"});

        JsonNode result = service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of("id", 1)));

        assertEquals(1, result.get("id").asInt());
        assertEquals("surgery", result.get("specialty").asText());
    }

    // --- D. the list path builds its SQL through the same quoter ----------------------------------

    @Test
    void bothStatementsOfTheListPathUseTheDriverQuoteCharacter() throws Exception {
        stubRepositories(FLAT_MAPPING_JSON, true);
        stubDriverQuote(BACKTICK);

        ResultSet pkRs = org.mockito.Mockito.mock(ResultSet.class);
        ResultSetMetaData pkMeta = org.mockito.Mockito.mock(ResultSetMetaData.class);
        ResultSet rowRs = org.mockito.Mockito.mock(ResultSet.class);
        ResultSetMetaData rowMeta = org.mockito.Mockito.mock(ResultSetMetaData.class);

        when(jdbcConn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(pkRs, rowRs);

        when(pkRs.next()).thenReturn(true, false);
        when(pkRs.getMetaData()).thenReturn(pkMeta);
        when(pkMeta.getColumnCount()).thenReturn(1);
        when(pkMeta.getColumnLabel(1)).thenReturn("petclinic__vets__id");
        when(pkRs.getObject(1)).thenReturn(7);

        when(rowRs.next()).thenReturn(true);
        when(rowRs.getMetaData()).thenReturn(rowMeta);
        when(rowMeta.getColumnCount()).thenReturn(2);
        when(rowMeta.getColumnLabel(1)).thenReturn("petclinic__vets__id");
        when(rowMeta.getColumnLabel(2)).thenReturn("petclinic__vets__first_name");
        when(rowRs.getObject(1)).thenReturn(7);
        when(rowRs.getObject(2)).thenReturn("James");

        JsonNode result = service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of()));

        verify(jdbcConn, times(2)).prepareStatement(sqlCaptor.capture());
        List<String> statements = sqlCaptor.getAllValues();

        assertEquals("SELECT `petclinic`.`vets`.`id` AS `petclinic__vets__id` FROM `petclinic`.`vets`",
                statements.get(0));
        assertEquals("SELECT `petclinic`.`vets`.`id` AS `petclinic__vets__id`, "
                     + "`petclinic`.`vets`.`first_name` AS `petclinic__vets__first_name` "
                     + "FROM `petclinic`.`vets` "
                     + "WHERE `petclinic`.`vets`.`id` = ?",
                statements.get(1));

        assertTrue(result.isArray());
        assertEquals(1, result.size());
        assertEquals("James", result.get(0).get("firstName").asText());
    }

    // --- E. identifiers from mapping JSON cannot break out of their quotes ------------------------

    @Test
    void anEmbeddedQuoteCharacterIsDoubledInBothTheColumnReferenceAndItsAlias() throws Exception {
        stubRepositories(QUOTE_IN_IDENTIFIER_MAPPING_JSON, false);
        stubDriverQuote(DOUBLE_QUOTE);
        stubSingleRow(new String[]{"petclinic__vets__we\"ird"}, new Object[]{1});

        service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of("id", 1)));

        assertEquals(
                "SELECT \"petclinic\".\"vets\".\"we\"\"ird\" AS \"petclinic__vets__we\"\"ird\" "
                + "FROM \"petclinic\".\"vets\" "
                + "WHERE \"petclinic\".\"vets\".\"we\"\"ird\" = ?",
                capturedSql());
    }

    // --- helpers ----------------------------------------------------------------------------------

    private String capturedSql() throws SQLException {
        verify(jdbcConn).prepareStatement(sqlCaptor.capture());
        return sqlCaptor.getValue();
    }

    // --- D. Oracle DATE must not drift a day ------------------------------------------------------

    /**
     * The reported defect. Oracle hands a {@code DATE} back as a Timestamp; serializing it as an
     * instant reads it as a local wall-clock value and re-expresses it in UTC, moving midnight onto
     * the previous day anywhere east of Greenwich.
     */
    @Test
    void oracleDateAtMidnightSerializesAsTheStoredDay() throws Exception {
        stubRepositories(VISIT_MAPPING_JSON, false);
        stubDriverQuote(DOUBLE_QUOTE);
        stubSingleRow(
                new String[]{"petclinic__visits__id", "petclinic__visits__visit_date"},
                new Object[]{1, java.sql.Timestamp.valueOf("2010-03-04 00:00:00")},
                new String[]{"NUMBER", "DATE"});

        JsonNode result = service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of()));

        assertEquals("2010-03-04", result.get("date").asText());
    }

    @Test
    void oracleDateWithATimeKeepsTheTimeRatherThanBeingTruncated() throws Exception {
        stubRepositories(VISIT_MAPPING_JSON, false);
        stubDriverQuote(DOUBLE_QUOTE);
        stubSingleRow(
                new String[]{"petclinic__visits__id", "petclinic__visits__visit_date"},
                new Object[]{1, java.sql.Timestamp.valueOf("2010-03-04 14:30:15")},
                new String[]{"NUMBER", "DATE"});

        JsonNode result = service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of()));

        assertEquals("2010-03-04T14:30:15", result.get("date").asText());
    }

    /**
     * The regression guard. PostgreSQL reports {@code timestamptz} with the same type code as
     * {@code timestamp}, and its value is a genuine instant, so it must keep the instant conversion.
     * Widening the date-only rule to every timestamp would shift this one in the opposite direction.
     */
    @Test
    void postgresTimestamptzKeepsItsInstantConversion() throws Exception {
        stubRepositories(VISIT_MAPPING_JSON, false);
        stubDriverQuote(DOUBLE_QUOTE);
        java.sql.Timestamp ts = java.sql.Timestamp.valueOf("2010-03-04 00:00:00");
        stubSingleRow(
                new String[]{"petclinic__visits__id", "petclinic__visits__visit_date"},
                new Object[]{1, ts},
                new String[]{"int4", "timestamptz"});

        JsonNode result = service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of()));

        assertEquals(ts.toInstant().toString(), result.get("date").asText());
    }

    /** A vendor that reports a date-only column as java.sql.Date needs no correction at all. */
    @Test
    void sqlDateIsUnaffected() throws Exception {
        stubRepositories(VISIT_MAPPING_JSON, false);
        stubDriverQuote(DOUBLE_QUOTE);
        stubSingleRow(
                new String[]{"petclinic__visits__id", "petclinic__visits__visit_date"},
                new Object[]{1, java.sql.Date.valueOf("2010-03-04")},
                new String[]{"int4", "date"});

        JsonNode result = service.fetchData(ENDPOINT_ID, requestWithFilters(Map.of()));

        assertEquals("2010-03-04", result.get("date").asText());
    }

    private DataFetchRequest requestWithFilters(Map<String, Object> filters) {
        DataFetchRequest request = new DataFetchRequest();
        request.setFilters(filters);
        return request;
    }

    private void stubRepositories(String mappingJson, boolean listResponse) throws Exception {
        Database database = new Database();
        database.setDatabaseId(DATABASE_ID);

        Service serviceEntity = new Service();
        serviceEntity.setDatabase(database);

        Endpoint endpoint = new Endpoint();
        endpoint.setEndpointId(ENDPOINT_ID);
        endpoint.setService(serviceEntity);
        endpoint.setListResponse(listResponse);

        Mapping mapping = new Mapping();
        mapping.setMappingData(objectMapper.readTree(mappingJson));

        when(mappingRepository.findByEndpoint_EndpointId(ENDPOINT_ID)).thenReturn(Optional.of(mapping));
        when(endpointRepository.findByIdWithServiceAndDatabase(ENDPOINT_ID)).thenReturn(Optional.of(endpoint));
        when(connectionRepository.findActiveByDatabaseId(DATABASE_ID)).thenReturn(Optional.of(new Connection()));
        when(jdbcConnectionFactory.openConnection(any(Database.class), any(Connection.class))).thenReturn(jdbcConn);
    }

    private void stubDriverQuote(String identifierQuoteString) throws SQLException {
        when(jdbcConn.getMetaData()).thenReturn(jdbcMetaData);
        when(jdbcMetaData.getIdentifierQuoteString()).thenReturn(identifierQuoteString);
    }

    private void stubSingleRow(String[] columnLabels, Object[] values) throws SQLException {
        stubSingleRow(columnLabels, values, null);
    }

    private void stubSingleRow(String[] columnLabels, Object[] values, String[] columnTypeNames)
            throws SQLException {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        ResultSetMetaData meta = org.mockito.Mockito.mock(ResultSetMetaData.class);

        when(jdbcConn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(columnLabels.length);
        for (int i = 0; i < columnLabels.length; i++) {
            when(meta.getColumnLabel(i + 1)).thenReturn(columnLabels[i]);
            when(rs.getObject(i + 1)).thenReturn(values[i]);
            if (columnTypeNames != null) {
                when(meta.getColumnTypeName(i + 1)).thenReturn(columnTypeNames[i]);
            }
        }
    }
}
