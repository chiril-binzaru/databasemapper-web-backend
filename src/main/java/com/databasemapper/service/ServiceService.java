package com.databasemapper.service;

import com.databasemapper.dto.ServiceRequest;
import com.databasemapper.dto.ServiceResponse;

public interface ServiceService {

    ServiceResponse create(ServiceRequest request);
}
