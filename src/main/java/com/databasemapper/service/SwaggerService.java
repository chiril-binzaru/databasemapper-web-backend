package com.databasemapper.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface SwaggerService {

    JsonNode fetchAndSave(Long serviceId);

    JsonNode commit(Long serviceId);
}
