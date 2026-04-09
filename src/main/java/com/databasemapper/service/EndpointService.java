package com.databasemapper.service;

import com.databasemapper.dto.EndpointRequest;
import com.databasemapper.dto.EndpointReplaceRequest;
import com.databasemapper.dto.EndpointSyncResponse;
import com.databasemapper.dto.ServiceEndpointsResponse;

import java.util.List;

public interface EndpointService {

    ServiceEndpointsResponse getByServiceId(Long serviceId);

    ServiceEndpointsResponse create(Long serviceId, EndpointRequest request);

    EndpointSyncResponse syncEndpoints(Long serviceId);

    ServiceEndpointsResponse replaceEndpoints(Long serviceId, List<EndpointReplaceRequest> endpoints);
}
