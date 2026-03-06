package com.rise.backend.employee;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class EmployeeRepository {

    @NonNull
    private static final RowMapper<EmployeeResponse> EMPLOYEE_ROW_MAPPER = (rs, rowNum) -> new EmployeeResponse(
            rs.getInt("id"),
            rs.getString("employee_id"),
            rs.getString("full_name"),
            rs.getString("email"),
            rs.getInt("shop_id"),
            rs.getString("shop_code"),
            rs.getString("country_code"),
            rs.getBoolean("is_active")
    );

    private final JdbcTemplate jdbcTemplate;

    public EmployeeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Integer create(String employeeId, String fullName, String email, Integer shopId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO rise_core.employees(employee_id, full_name, email, shop_id)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """,
                Integer.class,
                employeeId,
                fullName,
                email,
                shopId
        );
    }

    public List<EmployeeResponse> findAll() {
        return jdbcTemplate.query(
                """
                SELECT e.id, e.employee_id, e.full_name, e.email, e.shop_id, s.shop_code, s.country_code, e.is_active
                FROM rise_core.employees e
                JOIN rise_core.shops s ON s.id = e.shop_id
                WHERE e.deleted_at IS NULL
                ORDER BY e.id
                """,
                EMPLOYEE_ROW_MAPPER
        );
    }

    public Optional<EmployeeResponse> findById(Integer id) {
        List<EmployeeResponse> rows = jdbcTemplate.query(
                """
                SELECT e.id, e.employee_id, e.full_name, e.email, e.shop_id, s.shop_code, s.country_code, e.is_active
                FROM rise_core.employees e
                JOIN rise_core.shops s ON s.id = e.shop_id
                WHERE e.id = ? AND e.deleted_at IS NULL
                """,
                EMPLOYEE_ROW_MAPPER,
                id
        );
        return rows.stream().findFirst();
    }

    public boolean existsByEmployeeIdIgnoreCase(String employeeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM rise_core.employees WHERE lower(employee_id) = lower(?) AND deleted_at IS NULL",
                Integer.class,
                employeeId
        );
        return count != null && count > 0;
    }

    public void update(Integer id, String fullName, String email, Integer shopId) {
        jdbcTemplate.update(
                """
                UPDATE rise_core.employees
                SET full_name = ?,
                    email = ?,
                    shop_id = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """,
                fullName,
                email,
                shopId,
                id
        );
    }

    public void deactivate(Integer id) {
        jdbcTemplate.update(
                """
                UPDATE rise_core.employees
                SET is_active = FALSE,
                    deleted_at = NOW(),
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """,
                id
        );
    }
}
