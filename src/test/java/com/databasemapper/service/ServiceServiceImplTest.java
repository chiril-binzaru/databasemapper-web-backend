package com.databasemapper.service;

import com.databasemapper.dto.ServiceRequest;
import com.databasemapper.dto.ServiceResponse;
import com.databasemapper.model.Database;
import com.databasemapper.repository.DatabaseRepository;
import com.databasemapper.repository.EndpointRepository;
import com.databasemapper.repository.MappingRepository;
import com.databasemapper.repository.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceServiceImplTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private DatabaseRepository databaseRepository;

    @Mock
    private MappingRepository mappingRepository;

    @Mock
    private EndpointRepository endpointRepository;

    @InjectMocks
    private ServiceServiceImpl serviceService;

    @Test
    void updateWithValidDatabaseIdUpdatesFieldsAndReassociatesDatabase() {
        Long serviceId = 1L;
        Long databaseId = 2L;

        com.databasemapper.model.Service existingService = new com.databasemapper.model.Service();
        existingService.setServiceId(serviceId);
        existingService.setServiceName("Old Name");
        existingService.setServiceBaseUrl("http://old-url");
        existingService.setSwaggerEndpoint("/old-swagger");

        Database database = new Database();
        database.setDatabaseId(databaseId);

        ServiceRequest request = new ServiceRequest();
        request.setDatabaseId(databaseId);
        request.setServiceName("New Name");
        request.setServiceBaseUrl("http://new-url");
        request.setSwaggerEndpoint("/new-swagger");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));
        when(databaseRepository.findById(databaseId)).thenReturn(Optional.of(database));
        when(serviceRepository.save(existingService)).thenReturn(existingService);

        ServiceResponse response = serviceService.update(serviceId, request);

        assertEquals(serviceId, response.getServiceId());
        assertEquals(databaseId, response.getDatabaseId());
        assertEquals("New Name", response.getServiceName());
        assertEquals("http://new-url", response.getServiceBaseUrl());
        assertEquals("/new-swagger", response.getSwaggerEndpoint());
        assertEquals(database, existingService.getDatabase());
        verify(serviceRepository).save(existingService);
    }

    @Test
    void updateWithNullDatabaseIdClearsDatabaseAssociation() {
        Long serviceId = 1L;

        Database database = new Database();
        database.setDatabaseId(5L);

        com.databasemapper.model.Service existingService = new com.databasemapper.model.Service();
        existingService.setServiceId(serviceId);
        existingService.setDatabase(database);
        existingService.setServiceName("Old Name");
        existingService.setServiceBaseUrl("http://old-url");

        ServiceRequest request = new ServiceRequest();
        request.setDatabaseId(null);
        request.setServiceName("New Name");
        request.setServiceBaseUrl("http://new-url");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));
        when(serviceRepository.save(existingService)).thenReturn(existingService);

        ServiceResponse response = serviceService.update(serviceId, request);

        assertNull(response.getDatabaseId());
        assertNull(existingService.getDatabase());
        verify(databaseRepository, never()).findById(any());
    }

    @Test
    void updateThrowsEntityNotFoundExceptionWhenServiceDoesNotExist() {
        Long serviceId = 99L;
        ServiceRequest request = new ServiceRequest();
        request.setServiceName("New Name");
        request.setServiceBaseUrl("http://new-url");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> serviceService.update(serviceId, request));
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void updateThrowsEntityNotFoundExceptionWhenDatabaseDoesNotExist() {
        Long serviceId = 1L;
        Long databaseId = 42L;

        com.databasemapper.model.Service existingService = new com.databasemapper.model.Service();
        existingService.setServiceId(serviceId);

        ServiceRequest request = new ServiceRequest();
        request.setDatabaseId(databaseId);
        request.setServiceName("New Name");
        request.setServiceBaseUrl("http://new-url");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));
        when(databaseRepository.findById(databaseId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> serviceService.update(serviceId, request));
        verify(serviceRepository, never()).save(any());
    }
}
