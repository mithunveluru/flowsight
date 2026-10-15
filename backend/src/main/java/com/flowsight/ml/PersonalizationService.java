package com.flowsight.ml;

import com.flowsight.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The single check for a user's personalization opt-out. When it returns false, callers
 * must not build per-user features or use the user's past feedback to adapt predictions;
 * global, non-personal models may still run.
 */
@Service
@RequiredArgsConstructor
public class PersonalizationService {

    private final JdbcTemplate jdbcTemplate;

    public boolean isEnabled(UUID userId) {
        if (userId == null) return false;
        List<Boolean> rows = jdbcTemplate.queryForList(
            "SELECT personalization_enabled FROM users WHERE id = ?", Boolean.class, userId);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }

    @Transactional
    public void setEnabled(UUID userId, boolean enabled) {
        int updated = jdbcTemplate.update(
            "UPDATE users SET personalization_enabled = ? WHERE id = ?", enabled, userId);
        if (updated == 0) throw new ResourceNotFoundException("User", userId);
    }
}
