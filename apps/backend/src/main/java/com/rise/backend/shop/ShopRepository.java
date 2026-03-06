package com.rise.backend.shop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ShopRepository {

    @NonNull
    private static final RowMapper<ShopResponse> SHOP_ROW_MAPPER = (rs, rowNum) -> new ShopResponse(
            rs.getInt("id"),
            rs.getString("shop_code"),
            rs.getString("name"),
            rs.getString("country_code"),
            rs.getString("region"),
            rs.getInt("business_unit_id"),
            rs.getBoolean("is_active")
    );

    private final JdbcTemplate jdbcTemplate;

    public ShopRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Integer create(String shopCode, String name, String countryCode, String region, Integer businessUnitId, boolean isActive) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO rise_core.shops(shop_code, name, country_code, region, business_unit_id, is_active)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Integer.class,
                shopCode,
                name,
                countryCode,
                region,
                businessUnitId,
                isActive
        );
    }

    public List<ShopResponse> findAll() {
        return jdbcTemplate.query(
                """
                SELECT id, shop_code, name, country_code, region, business_unit_id, is_active
                FROM rise_core.shops
                WHERE deleted_at IS NULL
                ORDER BY id
                """,
                SHOP_ROW_MAPPER
        );
    }

    public Optional<ShopResponse> findById(Integer id) {
        List<ShopResponse> rows = jdbcTemplate.query(
                """
                SELECT id, shop_code, name, country_code, region, business_unit_id, is_active
                FROM rise_core.shops
                WHERE id = ? AND deleted_at IS NULL
                """,
                SHOP_ROW_MAPPER,
                id
        );
        return rows.stream().findFirst();
    }

    public boolean existsByShopCodeIgnoreCase(String shopCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM rise_core.shops WHERE lower(shop_code) = lower(?) AND deleted_at IS NULL",
                Integer.class,
                shopCode
        );
        return count != null && count > 0;
    }

    public boolean existsByShopCodeIgnoreCaseAndIdNot(String shopCode, Integer id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM rise_core.shops WHERE lower(shop_code) = lower(?) AND id <> ? AND deleted_at IS NULL",
                Integer.class,
                shopCode,
                id
        );
        return count != null && count > 0;
    }

    public boolean businessUnitExists(Integer businessUnitId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM rise_core.business_units WHERE id = ?",
                Integer.class,
                businessUnitId
        );
        return count != null && count > 0;
    }

    public void update(Integer id, String shopCode, String name, String countryCode, String region, Integer businessUnitId, boolean isActive) {
        jdbcTemplate.update(
                """
                UPDATE rise_core.shops
                SET shop_code = ?,
                    name = ?,
                    country_code = ?,
                    region = ?,
                    business_unit_id = ?,
                    is_active = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """,
                shopCode,
                name,
                countryCode,
                region,
                businessUnitId,
                isActive,
                id
        );
    }

    public Optional<String> findCountryCodeByShopId(Integer shopId) {
        List<String> rows = jdbcTemplate.query(
                "SELECT country_code FROM rise_core.shops WHERE id = ? AND deleted_at IS NULL",
                (rs, rowNum) -> rs.getString("country_code"),
                shopId
        );
        return rows.stream().findFirst();
    }

    public boolean existsById(Integer id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM rise_core.shops WHERE id = ? AND deleted_at IS NULL",
                Integer.class,
                id
        );
        return count != null && count > 0;
    }
}
