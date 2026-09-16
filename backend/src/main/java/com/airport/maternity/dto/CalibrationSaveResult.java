package com.airport.maternity.dto;

import com.airport.maternity.entity.CalibrationRecord;
import com.airport.maternity.entity.DutyRosterEntry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 校准登记结果：
 * 校准记录、以及结论为「不通过」时被当场撤下的当班名单行（没有则为 null）。
 * 记录与撤下在同一事务内提交：要么都生效，要么都回滚成动手前的样子。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalibrationSaveResult {

    private CalibrationRecord calibration;

    /** 校准不通过时从当班可用名单撤下的那一行；校准通过或本来不在名单上时为 null */
    private DutyRosterEntry removedRosterEntry;
}
