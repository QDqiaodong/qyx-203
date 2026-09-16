package com.airport.maternity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 体温枪校准登记台账：值机对手里的体温枪逐台校准并登记
 * （选中枪号、写下校准人、结论是通过还是不通过）。
 *
 * 当班日（duty_date）同一把枪只允许落一份结论：唯一约束从数据库层兜底，
 * 两名值机抢着给同一把枪写结论时，先落地的提交，后到的直接冲突拒绝、不能改先写的结论。
 *
 * 结论为「不通过」时，当班可用名单里这把枪必须在同一事务内当场撤下，
 * 名单行记录撤下原因就是本次校准没过，绝不允许只留一份校准记录而枪还在当班名单里。
 */
@Entity
@Table(
        name = "calibration_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cal_device_duty_date",
                columnNames = {"device_id", "duty_date"}
        ),
        indexes = {
                @Index(name = "idx_cal_duty_date", columnList = "dutyDate"),
                @Index(name = "idx_cal_device_id", columnList = "deviceId")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalibrationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 设备编号快照：设备删除后台账仍能对上是哪把枪 */
    @Column(name = "device_code", length = 50)
    private String deviceCode;

    /** 校准人（值机） */
    @Column(name = "calibrator", nullable = false, length = 50)
    private String calibrator;

    /** 校准结论：通过 / 不通过（落库后不可修改，只追加不覆盖） */
    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "remark", length = 500)
    private String remark;

    /** 当班日：同一把枪每个当班日只认第一份落库结论 */
    @Column(name = "duty_date", nullable = false)
    private LocalDate dutyDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
