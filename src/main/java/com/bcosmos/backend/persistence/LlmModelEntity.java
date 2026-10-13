package com.bcosmos.backend.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * One model the gateway may call (spec v2 section 4.1, table {@code llm_model}).
 *
 * <p>There is no credential column and there never will be: API keys come from environment variables (ADR 0007,
 * spec section 10). {@code enabled} is the per-model opt-in that keeps code from leaving the machine unless the
 * user asked for it.</p>
 */
@Entity
@Table(name = "llm_model")
public class LlmModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 40)
    private LlmProvider provider;

    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private LlmModelKind kind;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "is_default", nullable = false)
    private boolean defaultModel;

    @Column(name = "context_window", nullable = false)
    private int contextWindow;

    @Column(name = "cost_per_1k_in", nullable = false, precision = 12, scale = 6)
    private BigDecimal costPer1kIn;

    @Column(name = "cost_per_1k_out", nullable = false, precision = 12, scale = 6)
    private BigDecimal costPer1kOut;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** For JPA only. */
    protected LlmModelEntity() {
    }

    public LlmModelEntity(LlmProvider provider, String modelName, LlmModelKind kind, boolean enabled,
            boolean defaultModel, int contextWindow, BigDecimal costPer1kIn, BigDecimal costPer1kOut, String notes,
            Instant now) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.modelName = Objects.requireNonNull(modelName, "modelName");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.enabled = enabled;
        this.defaultModel = defaultModel;
        this.contextWindow = contextWindow;
        this.costPer1kIn = Objects.requireNonNull(costPer1kIn, "costPer1kIn");
        this.costPer1kOut = Objects.requireNonNull(costPer1kOut, "costPer1kOut");
        this.notes = notes;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /** Applies an edit from the settings screen. {@code null} means "leave as it is" for optional values. */
    public void edit(Boolean newEnabled, Boolean newDefaultModel, Integer newContextWindow, BigDecimal newCostPer1kIn,
            BigDecimal newCostPer1kOut, String newNotes, Instant now) {
        if (newEnabled != null) {
            this.enabled = newEnabled;
        }
        if (newDefaultModel != null) {
            this.defaultModel = newDefaultModel;
        }
        if (newContextWindow != null) {
            this.contextWindow = newContextWindow;
        }
        if (newCostPer1kIn != null) {
            this.costPer1kIn = newCostPer1kIn;
        }
        if (newCostPer1kOut != null) {
            this.costPer1kOut = newCostPer1kOut;
        }
        if (newNotes != null) {
            this.notes = newNotes;
        }
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public Long getId() {
        return id;
    }

    public LlmProvider getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }

    public LlmModelKind getKind() {
        return kind;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDefaultModel() {
        return defaultModel;
    }

    public int getContextWindow() {
        return contextWindow;
    }

    public BigDecimal getCostPer1kIn() {
        return costPer1kIn;
    }

    public BigDecimal getCostPer1kOut() {
        return costPer1kOut;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
