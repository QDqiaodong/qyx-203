package com.airport.maternity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备删除结果：设备档案与其名下全部时段必须在同一事务内清掉，
 * 未结束占用先置为「已失效」并逐段写入变更记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDeleteResult {

    /** 随设备一并删除的时段总数 */
    private int totalSlots;

    /** 删除前被置为「已失效」的未结束占用段数（进行中 + 未开始） */
    private int invalidatedSlots;

    /** 删除时仍在进行中的占用段数 */
    private int ongoingSlots;

    /** 是否为强制删除（删除时仍存在进行中的占用） */
    private boolean forced;
}
