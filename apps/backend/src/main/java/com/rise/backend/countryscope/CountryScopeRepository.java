package com.rise.backend.countryscope;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class CountryScopeRepository {

    @NonNull
    private static final RowMapper<CountryScopeResponse> ROW_MAPPER = (rs, rowNum) -> new CountryScopeResponse(
            rs.getInt("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getBoolean("is_active")
    );

    private final JdbcTemplate jdbcTemplate;

    public CountryScopeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CountryScopeResponse> findAll(boolean includeInactive) {
        if (includeInactive) {
            return jdbcTemplate.query(
                    "SELECT id, code, name, is_active FROM rise_core.countries WHERE deleted_at IS NULL ORDER BY code",
                    ROW_MAPPER
            );
        }

        return jdbcTemplate.query(
                "SELECT id, code, name, is_active FROM rise_core.countries WHERE deleted_at IS NULL AND is_active = TRUE ORDER BY code",
                ROW_MAPPER
        );
    }

    public Optional<CountryScopeResponse> findById(Integer id) {
        List<CountryScopeResponse> rows = jdbcTemplate.query(
                "SELECT id, code, name, is_active FROM rise_core.countries WHERE id = ? AND deleted_at IS NULL",
                ROW_MAPPER,
                id
        );
        return rows.stream().findFirst();
    }

    public boolean existsByCode(String code) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM rise_core.countries WHERE lower(code) = lower(?) AND deleted_at IS NULL",
                Integer.class,
                code
        );
        return count != null && count > 0;
    }

    public boolean existsByCodeAndIdNot(String code, Integer id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM rise_core.countries WHERE lower(code) = lower(?) AND id <> ? AND deleted_at IS NULL",
                Integer.class,
                code,
                id
        );
        return count != null && count > 0;
    }

    public Integer create(String code, String name) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO rise_core.countries(code, name, is_active, created_at, updated_at)
                VALUES (?, ?, TRUE, NOW(), NOW())
                RETURNING id
                """,
                Integer.class,
                code.toUpperCase(Locale.ROOT),
                name
        );
    }

    public void update(Integer id, String code, String name, boolean isActive) {
        jdbcTemplate.update(
                """
                UPDATE rise_core.countries
                SET code = ?,
                    name = ?,
                    is_active = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """,
                code.toUpperCase(Locale.ROOT),
                name,
                isActive,
                id
        );
    }

    public void logicalDelete(Integer id) {
        jdbcTemplate.update(
                """
                UPDATE rise_core.countries
                SET is_active = FALSE,
                    deleted_at = NOW(),
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """,
                id
        );
    }
}
