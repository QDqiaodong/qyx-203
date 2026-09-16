package com.airport.maternity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 当班可用名单挂入入参：把某把枪列入当天当班可用名单。
 */
@Data
public class DutyRosterAddDTO {

    @NotNull(message = "枪号不能为空")
    private Long deviceId;

    /** 操作人（当班值机） */
    private String operator;
}
