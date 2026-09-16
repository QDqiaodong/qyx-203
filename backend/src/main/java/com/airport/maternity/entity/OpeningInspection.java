package com.airport.maternity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开班巡检台账：早班保洁对每台母婴设备的开班巡检登记。
 * 记录哪台设备、哪个分区、谁检的、检完是否缺巾缺纸；
 * 当天最近一次巡检结论不是「通过」时，禁止给该设备新挂「生效中」时段。
 */
@Entity
@Table(name = "opening_inspection", indexes = {
        @Index(name = "idx_oi_device_date", columnList = "deviceId, inspectionDate"),
        @Index(name = "idx_oi_date", columnList = "inspectionDate")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpeningInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 设备编号快照：设备删除后台账仍能对上编号 */
    @Column(name = "device_code", length = 50)
    private String deviceCode;

    /** 分区快照：随巡检落库，设备调区后历史巡检仍指向当时分区 */
    @Column(name = "terminal_area", length = 100)
    private String terminalArea;

    /** 巡检人（早班保洁） */
    @Column(name = "inspector", nullable = false, length = 50)
    private String inspector;

    /** 巡检结论：通过 / 不通过 */
    @Column(name = "result", nullable = false, length = 20)
    private String result;

    /** 检完是否缺巾 */
    @Column(name = "towel_shortage")
    private Boolean towelShortage = false;

    /** 检完是否缺纸 */
    @Column(name = "paper_shortage")
    private Boolean paperShortage = false;

    @Column(name = "remark", length = 500)
    private String remark;

    /** 巡检日期（开班日）：当天最近一次结论决定能否新挂「生效中」时段 */
    @Column(name = "inspection_date", nullable = false)
    private LocalDate inspectionDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
