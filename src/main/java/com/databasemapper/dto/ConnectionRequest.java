package com.databasemapper.dto;

import com.databasemapper.model.enums.ConnectionMode;
import com.databasemapper.validation.ValidConnectionRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ValidConnectionRequest
public class ConnectionRequest {

    @NotNull
    private ConnectionMode connectionMode;

    private Integer port;

    @Size(max = 255)
    private String username;

    @Size(max = 255)
    private String password;

    @Size(max = 4096)
    private String connectionString;
}
