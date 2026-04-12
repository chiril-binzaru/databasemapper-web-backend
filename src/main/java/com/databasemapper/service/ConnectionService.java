package com.databasemapper.service;

import com.databasemapper.dto.ConnectionRequest;
import com.databasemapper.dto.ConnectionResponse;

import java.util.List;

public interface ConnectionService {

    List<ConnectionResponse> getByDatabaseId(Long databaseId);

    ConnectionResponse create(Long databaseId, ConnectionRequest request);

    ConnectionResponse update(Long databaseId, Long connectionId, ConnectionRequest request);
}
