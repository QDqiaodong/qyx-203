package com.airport.maternity.dto;

import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.TimeSlot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 设备档案保存结果：
 * 设备停用或调区导致存量时段被置为失效时，随结果返回明细与原因（用于接口提示）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSaveResult {

    private Device device;
    private List<TimeSlot> invalidatedSlots;
    /** 时段被置失效的原因：设备停用 / 设备调区 */
    private String reason;
}
