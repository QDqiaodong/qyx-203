package com.airport.maternity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 高峰同时在用上限：按「航站楼分区 + 设备类型」配置，
 * 限制高峰窗内同一分区同一类型设备的同时在用台数。
 */
@Entity
@Table(name = "peak_capacity_limit", uniqueConstraints = {
        @UniqueConstraint(name = "uk_area_type", columnNames = {"terminal_area", "device_type"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeakCapacityLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "terminal_area", nullable = false, length = 100)
    private String terminalArea;

    @Column(name = "device_type", nullable = false, length = 50)
    private String deviceType;

    @Column(name = "max_concurrent", nullable = false)
    private Integer maxConcurrent;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
