package com.rise.backend.api;

import com.rise.backend.service.HealthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemController {

    private final HealthService healthService;

    @Value("${spring.application.name:rise-backend}")
    private String applicationName;

    @Value("${app.version:0.0.1-SNAPSHOT}")
    private String appVersion;

    public SystemController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/version")
    public VersionResponse version() {
        return new VersionResponse(applicationName, appVersion);
    }

    @GetMapping("/health")
    public HealthResponse health() {
        boolean databaseUp = healthService.isDatabaseUp();
        String databaseStatus = databaseUp ? "UP" : "DOWN";
        String appStatus = databaseUp ? "UP" : "DOWN";
        return new HealthResponse(appStatus, databaseStatus);
    }
}