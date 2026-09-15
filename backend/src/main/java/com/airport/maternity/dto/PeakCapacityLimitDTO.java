package com.airport.maternity.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PeakCapacityLimitDTO {

    private Long id;

    @NotBlank(message = "航站楼分区不能为空")
    private String terminalArea;

    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    @NotNull(message = "高峰同时在用上限不能为空")
    @Min(value = 0, message = "高峰同时在用上限不能为负数")
    private Integer maxConcurrent;
}
