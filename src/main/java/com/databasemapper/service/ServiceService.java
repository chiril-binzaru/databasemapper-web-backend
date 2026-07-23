package com.databasemapper.service;

import com.databasemapper.dto.ServiceRequest;
import com.databasemapper.dto.ServiceResponse;

import java.util.List;

public interface ServiceService {

    List<ServiceResponse> create(ServiceRequest request);

    ServiceResponse update(Long serviceId, ServiceRequest request);

    List<ServiceResponse> getAll();

    void delete(Long serviceId);
}
