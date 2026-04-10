package com.databasemapper.service;

import com.databasemapper.dto.EndpointSyncResponse;
import com.databasemapper.dto.SwaggerEndpointResponse;
import com.databasemapper.model.HttpMethod;
import com.databasemapper.model.Service;
import com.databasemapper.model.enums.SyncStatus;
import com.databasemapper.repository.EndpointRepository;
import com.databasemapper.repository.MappingRepository;
import com.databasemapper.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EndpointServiceImplTest {

    @Mock
    private EndpointRepository endpointRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private MappingRepository mappingRepository;

    @Mock
    private SwaggerService swaggerService;

    @InjectMocks
    private EndpointServiceImpl endpointService;

    @Test
    void syncEndpointsReturnsToAddAllWithoutPersistingWhenDbHasNoEndpoints() {
        Long serviceId = 1L;
        Service service = new Service();
        service.setServiceId(serviceId);

        List<SwaggerEndpointResponse> swaggerEndpoints = List.of(
                SwaggerEndpointResponse.builder()
                        .httpMethod(HttpMethod.GET)
                        .path("/users")
                        .build(),
                SwaggerEndpointResponse.builder()
                        .httpMethod(HttpMethod.POST)
                        .path("/users")
                        .build()
        );

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(swaggerService.fetchSwaggerEndpoints(serviceId)).thenReturn(swaggerEndpoints);
        when(endpointRepository.findAllByService_ServiceIdOrderByEndpointPathAscHttpMethodAsc(serviceId))
                .thenReturn(List.of());

        EndpointSyncResponse response = endpointService.syncEndpoints(serviceId);

        assertEquals(SyncStatus.TO_ADD_ALL, response.getSyncStatus());
        assertNotNull(response.getEndpoints());
        assertEquals(2, response.getEndpoints().size());
        assertEquals(HttpMethod.GET, response.getEndpoints().get(0).getHttpMethod());
        assertEquals("/users", response.getEndpoints().get(0).getPath());
        assertNull(response.getEndpoints().get(0).getStatus());
        assertEquals(HttpMethod.POST, response.getEndpoints().get(1).getHttpMethod());
        assertEquals("/users", response.getEndpoints().get(1).getPath());
        assertNull(response.getEndpoints().get(1).getStatus());
        verify(endpointRepository, never()).saveAll(org.mockito.ArgumentMatchers.<List<com.databasemapper.model.Endpoint>>any());
    }

    @Test
    void syncEndpointsReturnsUnchangedWhenSwaggerAndDbMatch() {
        Long serviceId = 2L;
        Service service = new Service();
        service.setServiceId(serviceId);

        com.databasemapper.model.Endpoint endpoint = new com.databasemapper.model.Endpoint();
        endpoint.setHttpMethod(HttpMethod.GET);
        endpoint.setEndpointPath("/health");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(swaggerService.fetchSwaggerEndpoints(serviceId)).thenReturn(List.of(
                SwaggerEndpointResponse.builder()
                        .httpMethod(HttpMethod.GET)
                        .path("/health")
                        .build()
        ));
        when(endpointRepository.findAllByService_ServiceIdOrderByEndpointPathAscHttpMethodAsc(serviceId))
                .thenReturn(List.of(endpoint));

        EndpointSyncResponse response = endpointService.syncEndpoints(serviceId);

        assertEquals(SyncStatus.UNCHANGED, response.getSyncStatus());
        assertNull(response.getEndpoints());
    }
}
