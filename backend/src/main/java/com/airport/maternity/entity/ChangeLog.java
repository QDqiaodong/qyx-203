package com.airport.maternity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "change_log", indexes = {
        @Index(name = "idx_cl_device_id", columnList = "deviceId"),
        @Index(name = "idx_change_time", columnList = "changeTime")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /**
     * 设备编号快照：设备从台账删除后，变更记录仍能对上是哪台编号的停用/删除与占用处置。
     */
    @Column(name = "device_code", length = 50)
    private String deviceCode;

    @Column(name = "time_slot_id")
    private Long timeSlotId;

    @Column(name = "change_type", nullable = false, length = 50)
    private String changeType;

    @Column(name = "before_value", columnDefinition = "TEXT")
    private String beforeValue;

    @Column(name = "after_value", columnDefinition = "TEXT")
    private String afterValue;

    @Column(name = "operator", length = 50)
    private String operator = "admin";

    @Column(name = "change_time")
    private LocalDateTime changeTime;

    @PrePersist
    protected void onCreate() {
        changeTime = LocalDateTime.now();
    }
}
