package com.databasemapper.service;

import com.databasemapper.dto.DatabaseRequest;
import com.databasemapper.dto.DatabaseResponse;

public interface DatabaseService {

    DatabaseResponse create(Long serviceId, DatabaseRequest request);
}
