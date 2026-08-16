package com.databasemapper.service;

import com.databasemapper.dto.SwaggerEndpointResponse;
import com.databasemapper.model.HttpMethod;
import com.databasemapper.repository.ServiceRepository;
import com.databasemapper.service.SwaggerService.EndpointModelResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SwaggerServiceImpl}.
 *
 * <p>Focus: the guard in the private {@code fetchRawSwagger} that rejects a service without a
 * configured swagger endpoint with 422 (instead of leaking a 500 through the global handler),
 * reached through both public entry points, plus the URL composition
 * {@code serviceBaseUrl + swaggerEndpoint} and the parsing of a hand-built OpenAPI document.
 *
 * <p>All collaborators are mocked - no HTTP call and no database are involved.
 */
@ExtendWith(MockitoExtension.class)
class SwaggerServiceImplTest {

    private static final Long SERVICE_ID = 1L;
    private static final String BASE_URL = "http://localhost:8080";
    private static final String SWAGGER_ENDPOINT = "/v3/api-docs";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private RestClient restClient;

    @SuppressWarnings("rawtypes")
    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @SuppressWarnings("rawtypes")
    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @InjectMocks
    private SwaggerServiceImpl swaggerService;

    @Captor
    private ArgumentCaptor<String> urlCaptor;

    // ------------------------------------------------------------------ fixtures

    private static com.databasemapper.model.Service service(String swaggerEndpoint) {
        com.databasemapper.model.Service service = new com.databasemapper.model.Service();
        service.setServiceId(SERVICE_ID);
        service.setServiceName("Petclinic Service");
        service.setServiceBaseUrl(BASE_URL);
        service.setSwaggerEndpoint(swaggerEndpoint);
        return service;
    }

    private static JsonNode json(String raw) {
        try {
            return OBJECT_MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid test fixture JSON", e);
        }
    }

    /** OpenAPI document with two supported methods and one method outside the {@link HttpMethod} enum. */
    private static JsonNode pathsSpec() {
        return json("""
                {
                  "paths": {
                    "/api/vets": {
                      "get":     { "responses": { "200": { "description": "ok" } } },
                      "post":    { "responses": { "201": { "description": "created" } } },
                      "options": { "responses": { "200": { "description": "ok" } } }
                    }
                  }
                }
                """);
    }

    /** OpenAPI document whose GET /api/vets 200 response is a {@code $ref} to an object schema. */
    private static JsonNode objectModelSpec() {
        return json("""
                {
                  "paths": {
                    "/api/vets": {
                      "get": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": { "$ref": "#/components/schemas/Vet" }
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "Vet": {
                        "type": "object",
                        "properties": {
                          "id": { "type": "integer" },
                          "specialties": {
                            "type": "array",
                            "items": { "$ref": "#/components/schemas/Specialty" }
                          }
                        }
                      },
                      "Specialty": {
                        "type": "object",
                        "properties": { "name": { "type": "string" } }
                      }
                    }
                  }
                }
                """);
    }

    /** Same endpoint, but the 200 response is {@code type: array} with {@code items.$ref}. */
    private static JsonNode arrayModelSpec() {
        return json("""
                {
                  "paths": {
                    "/api/vets": {
                      "get": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "array",
                                  "items": { "$ref": "#/components/schemas/Vet" }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "Vet": {
                        "type": "object",
                        "properties": { "id": { "type": "integer" } }
                      }
                    }
                  }
                }
                """);
    }

    @SuppressWarnings("unchecked")
    private void stubSwaggerCall(JsonNode body) {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(JsonNode.class)).thenReturn(body);
    }

    private void assertNoSwaggerEndpointFailure(ResponseStatusException exception) {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        assertNotNull(exception.getReason());
        assertTrue(exception.getReason().contains(String.valueOf(SERVICE_ID)),
                "Reason should name the service id, was: " + exception.getReason());
        assertTrue(exception.getReason().contains("has no swagger endpoint configured"),
                "Unexpected reason: " + exception.getReason());
    }

    // -------------------------------------------------- fetchSwaggerEndpoints: guards

    @Test
    void fetchSwaggerEndpointsThrowsUnprocessableEntityWhenSwaggerEndpointIsNull() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service(null)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> swaggerService.fetchSwaggerEndpoints(SERVICE_ID));

        assertNoSwaggerEndpointFailure(exception);
        verifyNoInteractions(restClient);
    }

    @Test
    void fetchSwaggerEndpointsThrowsUnprocessableEntityWhenSwaggerEndpointIsBlank() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service("   ")));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> swaggerService.fetchSwaggerEndpoints(SERVICE_ID));

        assertNoSwaggerEndpointFailure(exception);
        verifyNoInteractions(restClient);
    }

    @Test
    void fetchSwaggerEndpointsThrowsEntityNotFoundExceptionWhenServiceDoesNotExist() {
        when(serviceRepository.findById(99L)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> swaggerService.fetchSwaggerEndpoints(99L));

        // The service lookup happens before the swagger guard, so a missing service is a 404-style
        // EntityNotFoundException, never the 422.
        assertEquals("Service not found with id: 99", exception.getMessage());
        verifyNoInteractions(restClient);
    }

    // ---------------------------------------------- fetchSwaggerEndpoints: happy path

    @Test
    void fetchSwaggerEndpointsCallsBaseUrlPlusSwaggerEndpointAndParsesSupportedMethods() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service(SWAGGER_ENDPOINT)));
        stubSwaggerCall(pathsSpec());

        List<SwaggerEndpointResponse> endpoints = swaggerService.fetchSwaggerEndpoints(SERVICE_ID);

        verify(requestHeadersUriSpec).uri(urlCaptor.capture());
        assertEquals(BASE_URL + SWAGGER_ENDPOINT, urlCaptor.getValue());

        // "options" is not in the HttpMethod enum and must be skipped.
        assertEquals(2, endpoints.size(), "Expected only GET and POST to be parsed");
        assertTrue(endpoints.stream().anyMatch(e ->
                        e.getHttpMethod() == HttpMethod.GET && "/api/vets".equals(e.getPath())),
                "Expected GET /api/vets");
        assertTrue(endpoints.stream().anyMatch(e ->
                        e.getHttpMethod() == HttpMethod.POST && "/api/vets".equals(e.getPath())),
                "Expected POST /api/vets");
    }

    // ---------------------------------------------------- fetchResponseModels: guards

    @Test
    void fetchResponseModelsThrowsUnprocessableEntityWhenSwaggerEndpointIsNull() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service(null)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> swaggerService.fetchResponseModels(SERVICE_ID));

        assertNoSwaggerEndpointFailure(exception);
        verifyNoInteractions(restClient);
    }

    @Test
    void fetchResponseModelsThrowsUnprocessableEntityWhenSwaggerEndpointIsBlank() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service("   ")));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> swaggerService.fetchResponseModels(SERVICE_ID));

        assertNoSwaggerEndpointFailure(exception);
        verifyNoInteractions(restClient);
    }

    @Test
    void fetchResponseModelsThrowsEntityNotFoundExceptionWhenServiceDoesNotExist() {
        when(serviceRepository.findById(99L)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> swaggerService.fetchResponseModels(99L));

        assertEquals("Service not found with id: 99", exception.getMessage());
        verifyNoInteractions(restClient);
    }

    // ------------------------------------------------ fetchResponseModels: happy path

    @Test
    void fetchResponseModelsCallsBaseUrlPlusSwaggerEndpointAndResolvesObjectSchema() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service(SWAGGER_ENDPOINT)));
        stubSwaggerCall(objectModelSpec());

        Map<String, EndpointModelResult> models = swaggerService.fetchResponseModels(SERVICE_ID);

        verify(requestHeadersUriSpec).uri(urlCaptor.capture());
        assertEquals(BASE_URL + SWAGGER_ENDPOINT, urlCaptor.getValue());

        assertEquals(1, models.size());
        EndpointModelResult result = models.get("GET:/api/vets");
        assertNotNull(result, "Expected a model keyed GET:/api/vets, keys were: " + models.keySet());
        assertFalse(result.isList(), "An object schema must not be flagged as a list response");

        // The referenced root model and its nested $ref are both collected.
        assertTrue(result.responseModel().has("Vet"), "Root model Vet missing from response model");
        assertTrue(result.responseModel().has("Specialty"), "Nested model Specialty missing from response model");
    }

    @Test
    void fetchResponseModelsFlagsArraySchemaAsListResponse() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service(SWAGGER_ENDPOINT)));
        stubSwaggerCall(arrayModelSpec());

        Map<String, EndpointModelResult> models = swaggerService.fetchResponseModels(SERVICE_ID);

        assertEquals(1, models.size());
        EndpointModelResult result = models.get("GET:/api/vets");
        assertNotNull(result, "Expected a model keyed GET:/api/vets, keys were: " + models.keySet());
        assertTrue(result.isList(), "type: array with items.$ref must be flagged as a list response");
        assertTrue(result.responseModel().has("Vet"), "Root model Vet missing from response model");
    }
}
