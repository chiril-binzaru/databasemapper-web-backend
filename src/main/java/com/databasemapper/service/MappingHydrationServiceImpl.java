package com.databasemapper.service;

import com.databasemapper.config.JdbcConnectionFactory;
import com.databasemapper.dto.DataFetchRequest;
import com.databasemapper.dto.FieldDatabaseInfo;
import com.databasemapper.dto.JoinConditionPairDto;
import com.databasemapper.dto.JoinEntryDto;
import com.databasemapper.dto.MappingDto;
import com.databasemapper.dto.MappingFieldEntry;
import com.databasemapper.model.Database;
import com.databasemapper.model.HttpMethod;
import com.databasemapper.model.enums.FieldKind;
import com.databasemapper.repository.ConnectionRepository;
import com.databasemapper.repository.EndpointRepository;
import com.databasemapper.repository.MappingRepository;
import com.databasemapper.util.ColumnPath;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MappingHydrationServiceImpl implements MappingHydrationService {

    private final EndpointRepository endpointRepository;
    private final MappingRepository mappingRepository;
    private final ConnectionRepository connectionRepository;
    private final JdbcConnectionFactory jdbcConnectionFactory;
    private final ObjectMapper objectMapper;

    private record JoinStep(
            String knownSideQuoted,
            String newSideQuoted,
            String newTableRef,
            List<JoinConditionPairDto> additionalConditions
    ) {}

    private record ResolvedChildJoin(String parentColumnPath) {}

    @Override
    public JsonNode fetchData(String serviceName, String endpointPath, HttpMethod httpMethod, DataFetchRequest request) {
        Long endpointId = endpointRepository
                .findByServiceNameAndEndpointPathAndHttpMethod(serviceName, endpointPath, httpMethod)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Endpoint not found for service '%s', path '%s', method '%s'"
                        .formatted(serviceName, endpointPath, httpMethod)))
                .getEndpointId();
        return fetchData(endpointId, request);
    }

    @Override
    public JsonNode fetchData(Long endpointId, DataFetchRequest request) {
        MappingDto mapping = loadMapping(endpointId);

        var endpoint = endpointRepository.findByIdWithServiceAndDatabase(endpointId)
                .orElseThrow(() -> new EntityNotFoundException("Endpoint not found with id: " + endpointId));

        Database database = Optional.ofNullable(endpoint.getService().getDatabase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Endpoint's service has no linked database"));

        var connection = connectionRepository.findActiveByDatabaseId(database.getDatabaseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No active connection for database id: " + database.getDatabaseId()));

        Map<String, Object> columnFilters = resolveFilters(
                request.getFilters() != null ? request.getFilters() : Map.of(),
                mapping.getFieldMappings());

        List<JoinEntryDto> globalJoins = mapping.getJoins() != null ? mapping.getJoins() : List.of();

        try (java.sql.Connection conn = jdbcConnectionFactory.openConnection(database, connection)) {
            ObjectNode result = hydrateNode(mapping.getFieldMappings(), columnFilters, conn, globalJoins);
            if (result == null) {
                throw new EntityNotFoundException("No data found matching the provided filters");
            }
            return result;
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to fetch data from database: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Core recursive hydration
    // -------------------------------------------------------------------------

    private ObjectNode hydrateNode(
            List<MappingFieldEntry> fieldMappings,
            Map<String, Object> whereConditions,
            java.sql.Connection conn,
            List<JoinEntryDto> globalJoins) throws SQLException {

        // Tables this node queries from: WHERE columns + mapped VALUE field columns
        Set<String> nodeTableKeys = new LinkedHashSet<>();
        whereConditions.keySet().forEach(cp -> nodeTableKeys.add(ColumnPath.parse(cp).tableKey()));
        fieldMappings.stream()
                .filter(f -> f.getKind() == FieldKind.VALUE && columnPath(f) != null)
                .forEach(f -> nodeTableKeys.add(ColumnPath.parse(columnPath(f)).tableKey()));

        // Columns needed in the SELECT: mapped VALUE fields + parent-side of each child join
        Set<String> columnPathsToSelect = new LinkedHashSet<>();
        fieldMappings.stream()
                .filter(f -> f.getKind() == FieldKind.VALUE && columnPath(f) != null)
                .forEach(f -> columnPathsToSelect.add(columnPath(f)));

        Map<String, ResolvedChildJoin> childJoins = resolveChildJoins(fieldMappings, nodeTableKeys, globalJoins);
        childJoins.values().forEach(cj -> columnPathsToSelect.add(cj.parentColumnPath()));

        if (columnPathsToSelect.isEmpty() && whereConditions.isEmpty()) {
            return objectMapper.createObjectNode();
        }

        Map<String, Object> row = executeNodeQuery(columnPathsToSelect, whereConditions, globalJoins, conn);
        if (row == null) return null;

        ObjectNode result = objectMapper.createObjectNode();

        // VALUE fields
        for (MappingFieldEntry f : fieldMappings) {
            if (f.getKind() != FieldKind.VALUE) continue;
            String modelField = f.getServiceInfo().getModelField();
            String cp = columnPath(f);
            if (cp == null) {
                result.putNull(modelField);
            } else {
                putValue(result, modelField, row.get(ColumnPath.parse(cp).columnAlias()));
            }
        }

        // MODEL fields
        for (MappingFieldEntry f : fieldMappings) {
            if (f.getKind() != FieldKind.MODEL) continue;
            String modelField = f.getServiceInfo().getModelField();
            ResolvedChildJoin cj = childJoins.get(modelField);
            if (cj == null) {
                result.putNull(modelField);
                continue;
            }
            Object parentValue = row.get(ColumnPath.parse(cj.parentColumnPath()).columnAlias());
            if (parentValue == null) {
                result.putNull(modelField);
            } else {
                ObjectNode nested = hydrateNode(
                        f.getFieldMappings(), Map.of(cj.parentColumnPath(), parentValue), conn, globalJoins);
                result.set(modelField, nested != null ? nested : result.nullNode());
            }
        }

        // LIST_OF_MODELS fields
        for (MappingFieldEntry f : fieldMappings) {
            if (f.getKind() != FieldKind.LIST_OF_MODELS) continue;
            String modelField = f.getServiceInfo().getModelField();
            ResolvedChildJoin cj = childJoins.get(modelField);
            if (cj == null) {
                result.set(modelField, objectMapper.createArrayNode());
                continue;
            }
            Object parentValue = row.get(ColumnPath.parse(cj.parentColumnPath()).columnAlias());
            result.set(modelField, parentValue == null
                    ? objectMapper.createArrayNode()
                    : hydrateModelList(f, cj.parentColumnPath(), parentValue, conn, globalJoins));
        }

        // LIST_OF_VALUES fields
        for (MappingFieldEntry f : fieldMappings) {
            if (f.getKind() != FieldKind.LIST_OF_VALUES) continue;
            String modelField = f.getServiceInfo().getModelField();
            ResolvedChildJoin cj = childJoins.get(modelField);
            if (cj == null) {
                result.set(modelField, objectMapper.createArrayNode());
                continue;
            }
            Object parentValue = row.get(ColumnPath.parse(cj.parentColumnPath()).columnAlias());
            result.set(modelField, parentValue == null
                    ? objectMapper.createArrayNode()
                    : fetchValueList(f, cj.parentColumnPath(), parentValue, conn, globalJoins));
        }

        return result;
    }

    private ArrayNode hydrateModelList(
            MappingFieldEntry listField,
            String relationshipFilterColumnPath,
            Object parentJoinValue,
            java.sql.Connection conn,
            List<JoinEntryDto> globalJoins) throws SQLException {

        Optional<MappingFieldEntry> pkFieldOpt = listField.getFieldMappings().stream()
                .filter(f -> f.getKind() == FieldKind.VALUE
                        && f.getDatabaseInfo() != null
                        && Boolean.TRUE.equals(f.getDatabaseInfo().getPrimaryKey())
                        && f.getDatabaseInfo().getColumnPath() != null)
                .findFirst();

        if (pkFieldOpt.isPresent()) {
            return hydrateModelListByPk(
                    listField, pkFieldOpt.get(), relationshipFilterColumnPath, parentJoinValue, conn, globalJoins);
        }

        boolean hasNestedModelFields = listField.getFieldMappings().stream()
                .anyMatch(f -> f.getKind() == FieldKind.MODEL || f.getKind() == FieldKind.LIST_OF_MODELS);
        if (hasNestedModelFields) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No primary key field defined in LIST_OF_MODELS: "
                    + listField.getServiceInfo().getModelName()
                    + ". A primary key is required when list items contain nested MODEL or LIST_OF_MODELS fields.");
        }

        return hydrateModelListByRelationshipFilter(
                listField, relationshipFilterColumnPath, parentJoinValue, conn, globalJoins);
    }

    private ArrayNode hydrateModelListByPk(
            MappingFieldEntry listField,
            MappingFieldEntry pkField,
            String relationshipFilterColumnPath,
            Object parentJoinValue,
            java.sql.Connection conn,
            List<JoinEntryDto> globalJoins) throws SQLException {

        String pkColumnPath = pkField.getDatabaseInfo().getColumnPath();
        List<Map<String, Object>> pkRows = executeNodeQueryAll(
                Set.of(pkColumnPath),
                Map.of(relationshipFilterColumnPath, parentJoinValue),
                globalJoins,
                conn);

        ArrayNode array = objectMapper.createArrayNode();
        Set<Object> seenPkValues = new LinkedHashSet<>();
        for (Map<String, Object> pkRow : pkRows) {
            Object pkValue = pkRow.get(ColumnPath.parse(pkColumnPath).columnAlias());
            if (pkValue == null || !seenPkValues.add(pkValue)) continue;
            ObjectNode childNode = hydrateNode(
                    listField.getFieldMappings(), Map.of(pkColumnPath, pkValue), conn, globalJoins);
            if (childNode != null) array.add(childNode);
        }
        return array;
    }

    private ArrayNode hydrateModelListByRelationshipFilter(
            MappingFieldEntry listField,
            String relationshipFilterColumnPath,
            Object parentJoinValue,
            java.sql.Connection conn,
            List<JoinEntryDto> globalJoins) throws SQLException {

        Set<String> nodeTableKeys = new LinkedHashSet<>();
        nodeTableKeys.add(ColumnPath.parse(relationshipFilterColumnPath).tableKey());

        Set<String> columnPathsToSelect = new LinkedHashSet<>();
        listField.getFieldMappings().stream()
                .filter(f -> f.getKind() == FieldKind.VALUE && columnPath(f) != null)
                .forEach(f -> {
                    columnPathsToSelect.add(columnPath(f));
                    nodeTableKeys.add(ColumnPath.parse(columnPath(f)).tableKey());
                });

        Map<String, ResolvedChildJoin> childJoins = resolveChildJoins(listField.getFieldMappings(), nodeTableKeys, globalJoins);
        childJoins.values().forEach(cj -> columnPathsToSelect.add(cj.parentColumnPath()));

        List<Map<String, Object>> rows = executeNodeQueryAll(
                columnPathsToSelect, Map.of(relationshipFilterColumnPath, parentJoinValue), globalJoins, conn);

        ArrayNode array = objectMapper.createArrayNode();
        for (Map<String, Object> row : rows) {
            ObjectNode node = objectMapper.createObjectNode();

            for (MappingFieldEntry f : listField.getFieldMappings()) {
                if (f.getKind() != FieldKind.VALUE) continue;
                String modelField = f.getServiceInfo().getModelField();
                String cp = columnPath(f);
                if (cp == null) {
                    node.putNull(modelField);
                } else {
                    putValue(node, modelField, row.get(ColumnPath.parse(cp).columnAlias()));
                }
            }

            for (MappingFieldEntry f : listField.getFieldMappings()) {
                if (f.getKind() != FieldKind.LIST_OF_VALUES) continue;
                String modelField = f.getServiceInfo().getModelField();
                ResolvedChildJoin cj = childJoins.get(modelField);
                if (cj == null) {
                    node.set(modelField, objectMapper.createArrayNode());
                    continue;
                }
                Object parentVal = row.get(ColumnPath.parse(cj.parentColumnPath()).columnAlias());
                node.set(modelField, parentVal == null
                        ? objectMapper.createArrayNode()
                        : fetchValueList(f, cj.parentColumnPath(), parentVal, conn, globalJoins));
            }

            array.add(node);
        }
        return array;
    }

    private ArrayNode fetchValueList(
            MappingFieldEntry field,
            String relationshipFilterColumnPath,
            Object parentJoinValue,
            java.sql.Connection conn,
            List<JoinEntryDto> globalJoins) throws SQLException {

        String cp = columnPath(field);
        ArrayNode array = objectMapper.createArrayNode();
        List<Map<String, Object>> rows = executeNodeQueryAll(
                Set.of(cp),
                Map.of(relationshipFilterColumnPath, parentJoinValue),
                globalJoins,
                conn);
        String valueAlias = ColumnPath.parse(cp).columnAlias();
        for (Map<String, Object> row : rows) {
            addValue(array, row.get(valueAlias));
        }
        return array;
    }

    // -------------------------------------------------------------------------
    // Query building
    // -------------------------------------------------------------------------

    private record BuiltQuery(String sql, List<Object> params) {}

    private BuiltQuery buildNodeQuery(
            Set<String> columnPathsToSelect,
            Map<String, Object> whereConditions,
            List<JoinEntryDto> globalJoins) {

        if (whereConditions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one filter condition is required");
        }

        List<ColumnPath> selectCols = columnPathsToSelect.stream().map(ColumnPath::parse).toList();
        List<ColumnPath> whereCols = whereConditions.keySet().stream().map(ColumnPath::parse).toList();

        ColumnPath anchorWherePath = whereCols.get(0);
        String anchorTableKey = anchorWherePath.tableKey();

        Set<String> allTableKeys = new LinkedHashSet<>();
        allTableKeys.add(anchorTableKey);
        selectCols.forEach(c -> allTableKeys.add(c.tableKey()));
        whereCols.forEach(c -> allTableKeys.add(c.tableKey()));

        List<JoinStep> joinSteps = resolveJoinOrder(anchorTableKey, allTableKeys, globalJoins);

        StringBuilder sql = new StringBuilder("SELECT ");
        if (selectCols.isEmpty()) {
            sql.append("1");
        } else {
            sql.append(selectCols.stream()
                    .map(c -> c.quotedFull() + " AS \"" + c.columnAlias() + "\"")
                    .collect(Collectors.joining(", ")));
        }

        sql.append(" FROM ").append(anchorWherePath.quotedTableRef());

        // Params collected in SQL order: JOIN condition literals first, then WHERE values
        List<Object> params = new ArrayList<>();

        for (JoinStep step : joinSteps) {
            sql.append(" JOIN ").append(step.newTableRef())
               .append(" ON ").append(step.knownSideQuoted()).append(" = ").append(step.newSideQuoted());
            if (step.additionalConditions() != null) {
                for (JoinConditionPairDto addCond : step.additionalConditions()) {
                    appendAdditionalCondition(sql, params, addCond);
                }
            }
        }

        List<String> whereParts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : whereConditions.entrySet()) {
            whereParts.add(ColumnPath.parse(entry.getKey()).quotedFull() + " = ?");
            params.add(entry.getValue());
        }
        sql.append(" WHERE ").append(String.join(" AND ", whereParts));

        return new BuiltQuery(sql.toString(), params);
    }

    private Map<String, Object> executeNodeQuery(
            Set<String> columnPathsToSelect,
            Map<String, Object> whereConditions,
            List<JoinEntryDto> globalJoins,
            java.sql.Connection conn) throws SQLException {

        BuiltQuery q = buildNodeQuery(columnPathsToSelect, whereConditions, globalJoins);
        try (PreparedStatement stmt = conn.prepareStatement(q.sql())) {
            for (int i = 0; i < q.params().size(); i++) {
                stmt.setObject(i + 1, q.params().get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> row = new LinkedHashMap<>();
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                return row;
            }
        }
    }

    private List<Map<String, Object>> executeNodeQueryAll(
            Set<String> columnPathsToSelect,
            Map<String, Object> whereConditions,
            List<JoinEntryDto> globalJoins,
            java.sql.Connection conn) throws SQLException {

        BuiltQuery q = buildNodeQuery(columnPathsToSelect, whereConditions, globalJoins);
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(q.sql())) {
            for (int i = 0; i < q.params().size(); i++) {
                stmt.setObject(i + 1, q.params().get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Join resolution
    // -------------------------------------------------------------------------

    private Map<String, ResolvedChildJoin> resolveChildJoins(
            List<MappingFieldEntry> fieldMappings,
            Set<String> nodeTableKeys,
            List<JoinEntryDto> globalJoins) {

        Map<String, ResolvedChildJoin> result = new LinkedHashMap<>();

        for (MappingFieldEntry f : fieldMappings) {
            Set<String> childTableKeys = switch (f.getKind()) {
                case MODEL, LIST_OF_MODELS -> tableKeysFromValueFields(f.getFieldMappings());
                case LIST_OF_VALUES -> columnPath(f) != null
                        ? Set.of(ColumnPath.parse(columnPath(f)).tableKey())
                        : Set.of();
                default -> Set.of();
            };

            if (childTableKeys.isEmpty()) continue;

            JoinEntryDto join = findParentChildJoin(nodeTableKeys, childTableKeys, globalJoins);
            if (join == null) continue;

            result.put(f.getServiceInfo().getModelField(), new ResolvedChildJoin(parentSide(join, nodeTableKeys)));
        }
        return result;
    }

    private JoinEntryDto findParentChildJoin(
            Set<String> parentTableKeys,
            Set<String> childTableKeys,
            List<JoinEntryDto> globalJoins) {

        for (JoinEntryDto join : globalJoins) {
            ColumnPath left = ColumnPath.parse(join.getLeft());
            ColumnPath right = ColumnPath.parse(join.getRight());
            boolean leftInParent = parentTableKeys.contains(left.tableKey());
            boolean rightInChild = childTableKeys.contains(right.tableKey());
            boolean rightInParent = parentTableKeys.contains(right.tableKey());
            boolean leftInChild = childTableKeys.contains(left.tableKey());

            if ((leftInParent && rightInChild) || (rightInParent && leftInChild)) {
                return join;
            }
        }

        for (String parentTableKey : parentTableKeys) {
            for (String childTableKey : childTableKeys) {
                JoinEntryDto viaThirdTableJoin = findJoinViaThirdTable(parentTableKey, childTableKey, globalJoins);
                if (viaThirdTableJoin != null) {
                    return viaThirdTableJoin;
                }
            }
        }
        return null;
    }

    private JoinEntryDto findJoinViaThirdTable(
            String parentTableKey,
            String childTableKey,
            List<JoinEntryDto> globalJoins) {

        Set<String> parentAdjacentTables = adjacentTables(parentTableKey, globalJoins);
        Set<String> childAdjacentTables = adjacentTables(childTableKey, globalJoins);

        for (String bridgeTableKey : parentAdjacentTables) {
            if (!childAdjacentTables.contains(bridgeTableKey)) continue;

            for (JoinEntryDto join : globalJoins) {
                ColumnPath left = ColumnPath.parse(join.getLeft());
                ColumnPath right = ColumnPath.parse(join.getRight());
                boolean matchesParentAndBridge =
                        (left.tableKey().equals(parentTableKey) && right.tableKey().equals(bridgeTableKey))
                        || (right.tableKey().equals(parentTableKey) && left.tableKey().equals(bridgeTableKey));

                if (matchesParentAndBridge) {
                    return join;
                }
            }
        }

        return null;
    }

    private String parentSide(JoinEntryDto join, Set<String> parentTableKeys) {
        ColumnPath left = ColumnPath.parse(join.getLeft());
        return parentTableKeys.contains(left.tableKey()) ? join.getLeft() : join.getRight();
    }

    private String childSide(JoinEntryDto join, Set<String> parentTableKeys) {
        ColumnPath left = ColumnPath.parse(join.getLeft());
        return parentTableKeys.contains(left.tableKey()) ? join.getRight() : join.getLeft();
    }

    private Set<String> adjacentTables(String tableKey, List<JoinEntryDto> globalJoins) {
        Set<String> neighbors = new LinkedHashSet<>();
        for (JoinEntryDto join : globalJoins) {
            ColumnPath left = ColumnPath.parse(join.getLeft());
            ColumnPath right = ColumnPath.parse(join.getRight());
            if (left.tableKey().equals(tableKey)) {
                neighbors.add(right.tableKey());
            } else if (right.tableKey().equals(tableKey)) {
                neighbors.add(left.tableKey());
            }
        }
        return neighbors;
    }

    // BFS from anchor to reach every referenced table via the global joins list
    private List<JoinStep> resolveJoinOrder(
            String anchorTableKey,
            Set<String> allTableKeys,
            List<JoinEntryDto> globalJoins) {

        List<JoinStep> steps = new ArrayList<>();
        Set<String> inFrom = new LinkedHashSet<>();
        inFrom.add(anchorTableKey);

        Set<String> remaining = new LinkedHashSet<>(allTableKeys);
        remaining.remove(anchorTableKey);

        while (!remaining.isEmpty()) {
            boolean progress = false;

            Iterator<String> it = remaining.iterator();
            while (it.hasNext()) {
                String tableKey = it.next();
                List<JoinEntryDto> path = findJoinPath(tableKey, inFrom, globalJoins);
                if (path == null) continue;

                for (JoinEntryDto join : path) {
                    String newTableKey = addJoinStep(join, inFrom, steps);
                    if (newTableKey != null) {
                        inFrom.add(newTableKey);
                    }
                }

                it.remove();
                progress = true;
            }

            if (!progress) break;
        }

        if (!remaining.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No join path found for tables: " + remaining
                    + ". Add the required join conditions to the mapping.");
        }
        return steps;
    }

    private String addJoinStep(JoinEntryDto join, Set<String> knownTables, List<JoinStep> steps) {
        ColumnPath left = ColumnPath.parse(join.getLeft());
        ColumnPath right = ColumnPath.parse(join.getRight());
        boolean leftKnown = knownTables.contains(left.tableKey());
        boolean rightKnown = knownTables.contains(right.tableKey());

        if (leftKnown == rightKnown) {
            return null;
        }

        boolean rightIsNew = !rightKnown;
        steps.add(new JoinStep(
                rightIsNew ? left.quotedFull() : right.quotedFull(),
                rightIsNew ? right.quotedFull() : left.quotedFull(),
                rightIsNew ? right.quotedTableRef() : left.quotedTableRef(),
                join.getAdditionalJoinConditions()
        ));

        return rightIsNew ? right.tableKey() : left.tableKey();
    }

    private List<JoinEntryDto> findJoinPath(
            String targetTableKey,
            Set<String> knownTables,
            List<JoinEntryDto> globalJoins) {

        record PathNode(String tableKey, List<JoinEntryDto> path) {}

        Deque<PathNode> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>(knownTables);
        for (String knownTable : knownTables) {
            queue.addLast(new PathNode(knownTable, List.of()));
        }

        while (!queue.isEmpty()) {
            PathNode current = queue.removeFirst();
            if (current.tableKey().equals(targetTableKey)) {
                return current.path();
            }

            for (JoinEntryDto join : globalJoins) {
                ColumnPath left = ColumnPath.parse(join.getLeft());
                ColumnPath right = ColumnPath.parse(join.getRight());

                String nextTableKey = null;
                if (left.tableKey().equals(current.tableKey()) && !visited.contains(right.tableKey())) {
                    nextTableKey = right.tableKey();
                } else if (right.tableKey().equals(current.tableKey()) && !visited.contains(left.tableKey())) {
                    nextTableKey = left.tableKey();
                }

                if (nextTableKey != null) {
                    visited.add(nextTableKey);
                    List<JoinEntryDto> nextPath = new ArrayList<>(current.path());
                    nextPath.add(join);
                    queue.addLast(new PathNode(nextTableKey, nextPath));
                }
            }
        }

        return null;
    }

    private JoinEntryDto findDirectJoin(
            String targetTableKey,
            Set<String> knownTables,
            List<JoinEntryDto> globalJoins) {

        for (JoinEntryDto join : globalJoins) {
            ColumnPath left = ColumnPath.parse(join.getLeft());
            ColumnPath right = ColumnPath.parse(join.getRight());
            boolean leftKnown = knownTables.contains(left.tableKey());
            boolean rightKnown = knownTables.contains(right.tableKey());
            boolean leftIsTarget = left.tableKey().equals(targetTableKey);
            boolean rightIsTarget = right.tableKey().equals(targetTableKey);

            if ((leftKnown && rightIsTarget) || (rightKnown && leftIsTarget)) {
                return join;
            }
        }
        return null;
    }

    private void appendAdditionalCondition(StringBuilder sql, List<Object> params, JoinConditionPairDto cond) {
        sql.append(" AND ")
           .append(ColumnPath.parse(cond.getLeft()).quotedFull())
           .append(" ").append(cond.getOperator().toSql()).append(" ");

        if (cond.getRight() != null) {
            sql.append(ColumnPath.parse(cond.getRight()).quotedFull());
        } else if (cond.getRightLiteral() != null) {
            sql.append("?");
            params.add(cond.getRightLiteral());
        } else {
            String placeholders = cond.getRightValues().stream()
                    .map(v -> "?")
                    .collect(Collectors.joining(", "));
            sql.append("(").append(placeholders).append(")");
            params.addAll(cond.getRightValues());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String columnPath(MappingFieldEntry f) {
        FieldDatabaseInfo db = f.getDatabaseInfo();
        return db != null ? db.getColumnPath() : null;
    }

    private Set<String> tableKeysFromValueFields(List<MappingFieldEntry> fieldMappings) {
        return fieldMappings.stream()
                .filter(f -> f.getKind() == FieldKind.VALUE && columnPath(f) != null)
                .map(f -> ColumnPath.parse(columnPath(f)).tableKey())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, Object> resolveFilters(
            Map<String, Object> filters,
            List<MappingFieldEntry> rootFieldMappings) {

        if (filters.isEmpty()) return Map.of();

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String modelField = entry.getKey();
            String cp = rootFieldMappings.stream()
                    .filter(f -> f.getKind() == FieldKind.VALUE
                            && modelField.equals(f.getServiceInfo().getModelField()))
                    .map(this::columnPath)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Filter field not found in mapping: " + modelField));
            resolved.put(cp, entry.getValue());
        }
        return resolved;
    }

    private void putValue(ObjectNode node, String fieldName, Object value) {
        if (value == null) {
            node.putNull(fieldName);
        } else if (value instanceof java.sql.Timestamp ts) {
            node.put(fieldName, ts.toInstant().toString());
        } else if (value instanceof java.sql.Date d) {
            node.put(fieldName, d.toLocalDate().toString());
        } else if (value instanceof java.sql.Time t) {
            node.put(fieldName, t.toLocalTime().toString());
        } else {
            node.set(fieldName, objectMapper.convertValue(value, JsonNode.class));
        }
    }

    private void addValue(ArrayNode array, Object value) {
        if (value == null) {
            array.addNull();
        } else if (value instanceof java.sql.Timestamp ts) {
            array.add(ts.toInstant().toString());
        } else if (value instanceof java.sql.Date d) {
            array.add(d.toLocalDate().toString());
        } else if (value instanceof java.sql.Time t) {
            array.add(t.toLocalTime().toString());
        } else {
            array.add(objectMapper.convertValue(value, JsonNode.class));
        }
    }

    private MappingDto loadMapping(Long endpointId) {
        return mappingRepository.findByEndpoint_EndpointId(endpointId)
                .map(mapping -> {
                    try {
                        return objectMapper.treeToValue(mapping.getMappingData(), MappingDto.class);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("Stored mapping data could not be deserialized", e);
                    }
                })
                .orElseThrow(() -> new EntityNotFoundException("Mapping not found for endpoint id: " + endpointId));
    }
}
