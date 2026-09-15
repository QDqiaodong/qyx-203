package com.airport.maternity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 高峰上限配置及其当前高峰同时在用数（用于列表展示）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PeakCapacityStatusDTO {

    private Long id;
    private String terminalArea;
    private String deviceType;
    private Integer maxConcurrent;
    /** 今日高峰窗内该分区该类型的同时在用峰值 */
    private Integer currentUsage;
    private LocalDateTime updatedAt;
}
