package com.rise.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final JdbcTemplate jdbcTemplate;

    public HealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isDatabaseUp() {
        try {
            Integer probe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return probe != null && probe == 1;
        } catch (Exception ex) {
            return false;
        }
    }
}