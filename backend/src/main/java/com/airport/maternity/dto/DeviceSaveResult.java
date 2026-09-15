package com.airport.maternity.dto;

import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.TimeSlot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 设备档案保存结果：调区导致部分尚未开始的时段被置为失效时，随结果返回明细。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSaveResult {

    private Device device;
    private List<TimeSlot> invalidatedSlots;
}
