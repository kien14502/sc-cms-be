package com.vnpt.mac.applications.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "validation_findings")
public class ValidationFindingEntity {
    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "rule_code", nullable = false, length = 100)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FindingSeverity severity;

    @Column(nullable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> context = Map.of();

    protected ValidationFindingEntity() {}

    public static ValidationFindingEntity create(UUID runId, String ruleCode, FindingSeverity severity, String message, Map<String, Object> context) {
        var entity = new ValidationFindingEntity();
        entity.id = UUID.randomUUID();
        entity.runId = runId;
        entity.ruleCode = ruleCode;
        entity.severity = severity;
        entity.message = message;
        entity.context = context == null ? Map.of() : context;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public String getRuleCode() { return ruleCode; }
    public FindingSeverity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Map<String, Object> getContext() { return context; }
}
