package com.airport.maternity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OpeningInspectionDTO {

    private Long id;

    @NotNull(message = "设备不能为空")
    private Long deviceId;

    @NotBlank(message = "巡检人不能为空")
    private String inspector;

    @NotBlank(message = "巡检结论不能为空")
    private String result;

    /** 检完是否缺巾 */
    private Boolean towelShortage = false;

    /** 检完是否缺纸 */
    private Boolean paperShortage = false;

    private String remark;
}
