package com.airport.maternity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 校准登记入参：选中枪号、写下校准人、结论是通过还是不通过。
 */
@Data
public class CalibrationDTO {

    @NotNull(message = "校准枪号不能为空")
    private Long deviceId;

    @NotBlank(message = "校准人不能为空")
    private String calibrator;

    @NotBlank(message = "校准结论不能为空")
    private String result;

    private String remark;
}
