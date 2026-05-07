package com.databasemapper.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class DataFetchRequest {

    private Map<String, Object> filters;
}
