package com.databasemapper.service;

import com.databasemapper.dto.SwaggerEndpointResponse;
import com.databasemapper.model.HttpMethod;
import com.databasemapper.repository.ServiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SwaggerServiceImpl implements SwaggerService {

    private final ServiceRepository serviceRepository;
    private final RestClient restClient;

    @Override
    public List<SwaggerEndpointResponse> fetchSwaggerEndpoints(Long serviceId) {
        com.databasemapper.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service not found with id: " + serviceId));

        JsonNode swagger = fetchRawSwagger(service);
        return parseEndpoints(swagger);
    }

    @Override
    public Map<String, EndpointModelResult> fetchResponseModels(Long serviceId) {
        com.databasemapper.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service not found with id: " + serviceId));

        JsonNode swagger = fetchRawSwagger(service);
        JsonNode paths = swagger.path("paths");
        JsonNode schemas = swagger.path("components").path("schemas");

        Map<String, EndpointModelResult> result = new HashMap<>();

        paths.fieldNames().forEachRemaining(path -> {
            JsonNode pathItem = paths.get(path);
            pathItem.fieldNames().forEachRemaining(method -> {
                try {
                    HttpMethod.valueOf(method.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return;
                }

                JsonNode schema = resolveSuccessSchema(pathItem.get(method).path("responses"));
                if (schema == null) return;

                EndpointModelResult model = extractModel(schema, schemas);
                if (model != null) {
                    result.put(method.toUpperCase() + ":" + path, model);
                }
            });
        });

        return result;
    }

    private JsonNode resolveSuccessSchema(JsonNode responses) {
        for (String code : List.of("200", "201")) {
            JsonNode schema = responses.path(code)
                    .path("content")
                    .path("application/json")
                    .path("schema");
            if (!schema.isMissingNode()) return schema;
        }
        return null;
    }

    private EndpointModelResult extractModel(JsonNode schema, JsonNode schemas) {
        if (schema.has("$ref")) {
            JsonNode model = resolveRef(schema.get("$ref").asText(), schemas);
            return model != null ? new EndpointModelResult(model, false) : null;
        }
        if ("array".equals(schema.path("type").asText()) && schema.path("items").has("$ref")) {
            JsonNode model = resolveRef(schema.path("items").get("$ref").asText(), schemas);
            return model != null ? new EndpointModelResult(model, true) : null;
        }
        return null;
    }

    private JsonNode resolveRef(String ref, JsonNode schemas) {
        String rootModelName = ref.substring(ref.lastIndexOf('/') + 1);
        JsonNode rootSchema = schemas.path(rootModelName);
        if (rootSchema.isMissingNode()) return null;

        ObjectNode result = JsonNodeFactory.instance.objectNode();
        collectSchemas(rootModelName, rootSchema, schemas, result, new HashSet<>());
        return result;
    }

    private void collectSchemas(String modelName, JsonNode schema, JsonNode allSchemas,
                                ObjectNode result, Set<String> visited) {
        if (!visited.add(modelName)) return;
        result.set(modelName, schema);
        collectRefs(schema, allSchemas, result, visited);
    }

    private void collectRefs(JsonNode node, JsonNode allSchemas, ObjectNode result, Set<String> visited) {
        if (node.isObject()) {
            if (node.has("$ref")) {
                String ref = node.get("$ref").asText();
                String modelName = ref.substring(ref.lastIndexOf('/') + 1);
                JsonNode referencedSchema = allSchemas.path(modelName);
                if (!referencedSchema.isMissingNode()) {
                    collectSchemas(modelName, referencedSchema, allSchemas, result, visited);
                }
            } else {
                node.fields().forEachRemaining(entry ->
                        collectRefs(entry.getValue(), allSchemas, result, visited));
            }
        } else if (node.isArray()) {
            node.elements().forEachRemaining(element ->
                    collectRefs(element, allSchemas, result, visited));
        }
    }

    private JsonNode fetchRawSwagger(com.databasemapper.model.Service service) {
        if (service.getSwaggerEndpoint() == null || service.getSwaggerEndpoint().isBlank()) {
            throw new IllegalStateException(
                    "Service with id " + service.getServiceId() + " has no swagger endpoint configured");
        }

        String url = service.getServiceBaseUrl() + service.getSwaggerEndpoint();

        return restClient.get()
                .uri(url)
                .retrieve()
                .body(JsonNode.class);
    }

    private List<SwaggerEndpointResponse> parseEndpoints(JsonNode swagger) {
        List<SwaggerEndpointResponse> result = new ArrayList<>();
        JsonNode paths = swagger.path("paths");

        paths.fieldNames().forEachRemaining(path -> {
            JsonNode pathItem = paths.get(path);
            pathItem.fieldNames().forEachRemaining(method -> {
                try {
                    HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
                    result.add(SwaggerEndpointResponse.builder()
                            .httpMethod(httpMethod)
                            .path(path)
                            .build());
                } catch (IllegalArgumentException ignored) {
                    // skip methods not in our enum (OPTIONS, HEAD, TRACE, etc.)
                }
            });
        });

        return result;
    }
}
