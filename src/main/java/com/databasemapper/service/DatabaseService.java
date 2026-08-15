package com.databasemapper.service;

import com.databasemapper.dto.DatabaseRequest;
import com.databasemapper.dto.DatabaseResponse;

public interface DatabaseService {

    DatabaseResponse getByServiceId(Long serviceId);

    DatabaseResponse create(Long serviceId, DatabaseRequest request);

    DatabaseResponse update(Long serviceId, DatabaseRequest request);
}
