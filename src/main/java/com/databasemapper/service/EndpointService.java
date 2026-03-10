package com.databasemapper.service;

import com.databasemapper.dto.EndpointRequest;
import com.databasemapper.dto.ServiceEndpointsResponse;

public interface EndpointService {

    ServiceEndpointsResponse create(EndpointRequest request);
}
