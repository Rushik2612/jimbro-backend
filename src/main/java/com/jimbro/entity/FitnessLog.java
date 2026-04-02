package com.jimbro.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fitness_logs")
public class FitnessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String logType; // "WORKOUT", "WEIGHT"

    private Double logValue; // e.g. weight in kg
    
    @Column(columnDefinition = "TEXT")
    private String metadata; // details, JSON string occasionally

    private LocalDateTime timestamp;

    public FitnessLog() {}

    public FitnessLog(Long userId, String logType, Double logValue, String metadata, LocalDateTime timestamp) {
        this.userId = userId;
        this.logType = logType;
        this.logValue = logValue;
        this.metadata = metadata;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getLogType() { return logType; }
    public void setLogType(String logType) { this.logType = logType; }

    public Double getLogValue() { return logValue; }
    public void setLogValue(Double logValue) { this.logValue = logValue; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
