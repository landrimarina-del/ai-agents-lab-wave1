package com.rise.backend.businessunit;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class BusinessUnitRepository {

    @NonNull
    private static final RowMapper<BusinessUnit> BUSINESS_UNIT_ROW_MAPPER = (rs, rowNum) -> new BusinessUnit(
            rs.getInt("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("country_code"),
            rs.getString("region"),
            rs.getBoolean("is_active")
    );

    private final JdbcTemplate jdbcTemplate;

    public BusinessUnitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Integer create(String code, String name, String countryCode, String region, boolean isActive) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO rise_core.business_units(code, name, country_code, region, is_active)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """,
                Integer.class,
                code,
                name,
                countryCode,
                region,
                isActive
        );
    }

    public List<BusinessUnit> findAll() {
        return jdbcTemplate.query(
                "SELECT id, code, name, country_code, region, is_active FROM rise_core.business_units ORDER BY id",
                BUSINESS_UNIT_ROW_MAPPER
        );
    }

    public Optional<BusinessUnit> findById(Integer id) {
        List<BusinessUnit> rows = jdbcTemplate.query(
                "SELECT id, code, name, country_code, region, is_active FROM rise_core.business_units WHERE id = ?",
                BUSINESS_UNIT_ROW_MAPPER,
                id
        );
        return rows.stream().findFirst();
    }

    public boolean existsByCodeIgnoreCase(String code) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM rise_core.business_units WHERE lower(code) = lower(?)",
                Integer.class,
                code
        );
        return count != null && count > 0;
    }

    public boolean existsByCodeIgnoreCaseAndIdNot(String code, Integer id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM rise_core.business_units WHERE lower(code) = lower(?) AND id <> ?",
                Integer.class,
                code,
                id
        );
        return count != null && count > 0;
    }

    public void update(Integer id, String code, String name, String countryCode, String region, boolean isActive) {
        jdbcTemplate.update(
                """
                UPDATE rise_core.business_units
                SET code = ?,
                    name = ?,
                    country_code = ?,
                    region = ?,
                    is_active = ?,
                    updated_at = NOW()
                WHERE id = ?
                """,
                code,
                name,
                countryCode,
                region,
                isActive,
                id
        );
    }

    public void deleteById(Integer id) throws DataIntegrityViolationException {
        jdbcTemplate.update(
                "DELETE FROM rise_core.business_units WHERE id = ?",
                id
        );
    }

    public int countExistingIds(List<Integer> businessUnitIds) {
        if (businessUnitIds == null || businessUnitIds.isEmpty()) {
            return 0;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(businessUnitIds.size(), "?"));
        String sql = "SELECT count(1) FROM rise_core.business_units WHERE id IN (" + placeholders + ")";

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, businessUnitIds.toArray());
        return count == null ? 0 : count;
    }

    public void replaceUserBusinessUnits(Integer userId, List<Integer> businessUnitIds) {
        jdbcTemplate.update("DELETE FROM rise_core.user_business_units WHERE user_id = ?", userId);

        if (businessUnitIds == null || businessUnitIds.isEmpty()) {
            return;
        }

        List<Object[]> batchParams = new ArrayList<>();
        for (Integer businessUnitId : businessUnitIds) {
            batchParams.add(new Object[]{userId, businessUnitId});
        }

        jdbcTemplate.batchUpdate(
                "INSERT INTO rise_core.user_business_units(user_id, business_unit_id) VALUES (?, ?)",
                batchParams
        );
    }
}
