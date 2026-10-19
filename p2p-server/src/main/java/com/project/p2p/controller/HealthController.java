package com.project.p2p.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "p2p-coordination-server");
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            status.put("database", connection.getCatalog());
            status.put("databaseProductName", metaData.getDatabaseProductName());
            status.put("databaseProductVersion", metaData.getDatabaseProductVersion());
            status.put("databaseConnected", true);
        } catch (Exception e) {
            status.put("databaseConnected", false);
            status.put("databaseError", e.getMessage());
        }
        return ResponseEntity.ok(status);
    }
}
