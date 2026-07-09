package com.airport.maternity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceDTO {

    private Long id;

    @NotBlank(message = "设备编号不能为空")
    private String deviceCode;

    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    @NotBlank(message = "航站楼分区不能为空")
    private String terminalArea;

    private String status = "正常";
}