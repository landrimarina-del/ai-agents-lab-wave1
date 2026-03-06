package com.rise.backend.user;

import com.rise.backend.auth.AppUser;
import com.rise.backend.auth.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    @NonNull
    private static final RowMapper<AppUser> USER_ROW_MAPPER = (rs, rowNum) -> {
        Array countryScopeArray = rs.getArray("country_scope");
        List<String> countryScope = Collections.emptyList();
        if (countryScopeArray != null) {
            Object value = countryScopeArray.getArray();
            if (value instanceof String[] values) {
                countryScope = Arrays.asList(values);
            }
        }

        Timestamp lockedUntil = rs.getTimestamp("locked_until");
        OffsetDateTime lockedUntilDateTime = lockedUntil == null ? null : lockedUntil.toInstant().atOffset(OffsetDateTime.now().getOffset());

        return new AppUser(
                rs.getInt("id"),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role")),
                countryScope,
                rs.getBoolean("is_active"),
                rs.getInt("failed_attempts"),
                lockedUntilDateTime
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AppUser> findByEmail(String email) {
        List<AppUser> users = jdbcTemplate.query(
                "SELECT * FROM rise_core.users WHERE lower(email) = lower(?)",
                USER_ROW_MAPPER,
                email
        );
        return users.stream().findFirst();
    }

    public Optional<AppUser> findById(Integer id) {
        List<AppUser> users = jdbcTemplate.query(
                "SELECT * FROM rise_core.users WHERE id = ?",
                USER_ROW_MAPPER,
                id
        );
        return users.stream().findFirst();
    }

    public List<AppUser> findAll() {
        return jdbcTemplate.query(
                "SELECT * FROM rise_core.users ORDER BY id",
                USER_ROW_MAPPER
        );
    }

    public boolean existsById(Integer id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM rise_core.users WHERE id = ?",
                Integer.class,
                id
        );
        return count != null && count > 0;
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM rise_core.users WHERE lower(email) = lower(?)",
                Integer.class,
                email
        );
        return count != null && count > 0;
    }

    public Integer createUser(String email, String fullName, String passwordHash, Role role, List<String> countryScope) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO rise_core.users(email, full_name, password_hash, role, country_scope)
                VALUES (?, ?, ?, ?, ?::text[])
                RETURNING id
                """,
                Integer.class,
                email,
                fullName,
                passwordHash,
                role.name(),
                toPgTextArray(countryScope)
        );
    }

    public void setFailedAttemptsAndLock(String email, int failedAttempts, OffsetDateTime lockedUntil) {
        jdbcTemplate.update(
                """
                UPDATE rise_core.users
                SET failed_attempts = ?,
                    locked_until = ?,
                    updated_at = NOW()
                WHERE lower(email) = lower(?)
                """,
                failedAttempts,
                lockedUntil,
                email
        );
    }

    public void resetLockoutAndAttempts(Integer userId) {
        jdbcTemplate.update(
                """
                UPDATE rise_core.users
                SET failed_attempts = 0,
                    locked_until = NULL,
                    updated_at = NOW()
                WHERE id = ?
                """,
                userId
        );
    }

    public void deactivateUser(Integer userId) {
        jdbcTemplate.update(
                """
                UPDATE rise_core.users
                SET is_active = FALSE,
                    updated_at = NOW()
                WHERE id = ?
                """,
                userId
        );
    }

    public void reactivateUser(Integer userId) {
        jdbcTemplate.update(
                """
                UPDATE rise_core.users
                SET is_active = TRUE,
                    failed_attempts = 0,
                    locked_until = NULL,
                    updated_at = NOW()
                WHERE id = ?
                """,
                userId
        );
    }

    private String toPgTextArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }

        StringBuilder builder = new StringBuilder("{");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append('"').append(values.get(index).replace("\"", "")).append('"');
        }
        builder.append('}');
        return builder.toString();
    }
}
