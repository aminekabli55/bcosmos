package com.bcosmos.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * One setting the browser may change (spec v2 section 9, table {@code app_setting}).
 *
 * <p>The table exists so a switch such as the AI master switch can be edited from the Settings screen instead of
 * being a deployment property. Nothing secret is ever stored here: credentials stay in environment variables
 * (ADR 0007, spec section 10).</p>
 */
@Entity
@Table(name = "app_setting")
public class AppSettingEntity {

    @Id
    @Column(name = "setting_key", nullable = false, length = 80)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String settingValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** For JPA only. */
    protected AppSettingEntity() {
    }

    public AppSettingEntity(String settingKey, String settingValue, Instant now) {
        this.settingKey = Objects.requireNonNull(settingKey, "settingKey");
        this.settingValue = Objects.requireNonNull(settingValue, "settingValue");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    /** Replaces the stored value of an existing row. */
    public void update(String newValue, Instant now) {
        this.settingValue = Objects.requireNonNull(newValue, "newValue");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
