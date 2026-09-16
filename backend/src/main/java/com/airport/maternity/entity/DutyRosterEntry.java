package com.airport.maternity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 当班可用名单：当天值机手头可以当班使用的体温枪（及其他设备）。
 *
 * 名单按当班日逐台挂入，append-only：
 * - 在校准登记：行状态保持「当班可用」；
 * - 校准结论「不通过」：同一事务内当场置为「已撤下」，撤下原因写明「校准不通过」并关联校准记录，
 *   当班可用名单里从此不再出现这把枪；
 * - 校准没写完（事务回滚）时名单必须仍是动手前的样子，绝不允许枪已撤下却没有对应校准记录。
 */
@Entity
@Table(
        name = "duty_roster_entry",
        indexes = {
                @Index(name = "idx_roster_duty_date", columnList = "dutyDate"),
                @Index(name = "idx_roster_device_id", columnList = "deviceId")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DutyRosterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 设备编号快照：设备删除后名单留痕仍能对上枪号 */
    @Column(name = "device_code", length = 50)
    private String deviceCode;

    /** 当班日 */
    @Column(name = "duty_date", nullable = false)
    private LocalDate dutyDate;

    /** 名单状态：当班可用 / 已撤下 */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ON_DUTY;

    /** 撤下原因：校准不通过撤下时必须写明，对得上是哪次校准没过 */
    @Column(name = "remove_reason", length = 200)
    private String removeReason;

    /** 触发撤下的校准记录：校准不通过撤下时回填，名单行与校准台账可互相核对 */
    @Column(name = "calibration_id")
    private Long calibrationId;

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

    public static final String STATUS_ON_DUTY = "当班可用";
    public static final String STATUS_REMOVED = "已撤下";

    /** 校准结论为不通过时的撤下原因前缀 */
    public static final String REMOVE_REASON_CALIBRATION = "校准不通过";
}
