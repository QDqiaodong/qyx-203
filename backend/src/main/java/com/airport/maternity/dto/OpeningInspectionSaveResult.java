package com.airport.maternity.dto;

import com.airport.maternity.entity.OpeningInspection;
import com.airport.maternity.entity.TimeSlot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 开班巡检登记结果：补检「不通过」且名下还有进行中占用时，
 * 随结果返回这些占用（它们继续有效、不置失效，只禁止再新挂），用于接口点名提示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpeningInspectionSaveResult {

    private OpeningInspection inspection;

    /** 登记「不通过」时仍在进行中的生效中占用（保持有效，不置失效） */
    private List<TimeSlot> ongoingSlots;
}
