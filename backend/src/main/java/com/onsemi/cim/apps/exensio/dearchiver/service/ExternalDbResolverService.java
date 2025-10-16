package com.onsemi.cim.apps.exensio.dearchiver.service;

import com.onsemi.cim.apps.exensio.dearchiver.entity.ExternalLocation;
import com.onsemi.cim.apps.exensio.dearchiver.config.ExternalDbConfig;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;

@Service
public class ExternalDbResolverService {
    private final ExternalDbConfig externalDbConfig;

    public ExternalDbResolverService(ExternalDbConfig externalDbConfig) {
        this.externalDbConfig = externalDbConfig;
    }

    public Connection resolveConnectionForLocation(ExternalLocation location, String environment) throws SQLException {
        if (location == null) throw new SQLException("null location");
        String key = location.getDbConnectionName();
        return externalDbConfig.getConnectionByKey(key, environment);
    }
}
