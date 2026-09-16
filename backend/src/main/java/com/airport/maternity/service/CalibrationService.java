package com.airport.maternity.service;

import com.airport.maternity.dto.CalibrationDTO;
import com.airport.maternity.dto.CalibrationSaveResult;
import com.airport.maternity.entity.CalibrationRecord;
import com.airport.maternity.entity.ChangeLog;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.DutyRosterEntry;
import com.airport.maternity.exception.CalibrationConflictException;
import com.airport.maternity.repository.CalibrationRecordRepository;
import com.airport.maternity.repository.ChangeLogRepository;
import com.airport.maternity.repository.DutyRosterEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 体温枪校准登记。
 *
 * 规则：
 * 1) 一把枪一个当班日只认第一份落库结论（设备行悲观锁 + (device_id,duty_date) 唯一约束双保险），
 *    两名值机抢着给同一把枪写结论，后到的直接冲突拒绝，不能改先写的结论；
 * 2) 结论「不通过」：当班可用名单里这把枪在同一事务内当场撤下，
 *    撤下原因写明就是本次校准没过，名单从此不再出现这把枪；
 * 3) 结论「通过」：名单上的枪继续留着，名单状态不动；
 * 4) 校准登记中途失败，事务整体回滚——不会出现枪已从名单消失、校准记录却没有。
 */
@Service
public class CalibrationService {

    public static final String RESULT_PASS = "通过";
    public static final String RESULT_FAIL = "不通过";

    @Autowired
    private CalibrationRecordRepository calibrationRepository;

    @Autowired
    private DutyRosterEntryRepository rosterRepository;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DutyRosterService dutyRosterService;

    @Autowired
    private ChangeLogRepository changeLogRepository;

    public Page<CalibrationRecord> findAll(Long deviceId, LocalDate dutyDate, Pageable pageable) {
        if (deviceId != null && dutyDate != null) {
            return calibrationRepository.findByDeviceIdAndDutyDate(deviceId, dutyDate, pageable);
        } else if (deviceId != null) {
            return calibrationRepository.findByDeviceId(deviceId, pageable);
        } else if (dutyDate != null) {
            return calibrationRepository.findByDutyDate(dutyDate, pageable);
        }
        return calibrationRepository.findAll(pageable);
    }

    public Optional<CalibrationRecord> findByDeviceAndDutyDate(Long deviceId, LocalDate dutyDate) {
        return calibrationRepository.findByDeviceIdAndDutyDate(deviceId, dutyDate);
    }

    @Transactional
    public CalibrationSaveResult save(CalibrationDTO dto) {
        if (!RESULT_PASS.equals(dto.getResult()) && !RESULT_FAIL.equals(dto.getResult())) {
            throw new IllegalArgumentException("校准结论只能是「通过」或「不通过」");
        }

        // 锁设备行：与另一笔并发校准、与名单挂入串行化
        Device device = deviceService.lockById(dto.getDeviceId());
        LocalDate today = LocalDate.now();

        // 先落地的那份为准：同日同枪已有结论，后到的不改不盖，直接冲突
        Optional<CalibrationRecord> existing =
                calibrationRepository.findByDeviceIdAndDutyDate(device.getId(), today);
        if (existing.isPresent()) {
            throw conflict(device, existing.get());
        }

        CalibrationRecord record = new CalibrationRecord();
        record.setDeviceId(device.getId());
        // 编号快照：设备删除后台账仍能对上枪号
        record.setDeviceCode(device.getDeviceCode());
        record.setCalibrator(dto.getCalibrator());
        record.setResult(dto.getResult());
        record.setRemark(dto.getRemark());
        record.setDutyDate(today);

        CalibrationRecord saved;
        try {
            saved = calibrationRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // 唯一约束兜底：锁外的极端并发也不允许落下第二份结论。
            // 直接抛出冲突（不再访问同一持久化上下文），事务随之回滚。
            throw new CalibrationConflictException(String.format(
                    "枪【%s】本当班日的校准结论已被另一笔登记抢先落地，先落地的为准，本次结论不予记录",
                    device.getDeviceCode()));
        }

        DutyRosterEntry removedEntry = null;
        if (RESULT_FAIL.equals(dto.getResult())) {
            // 校准没过：已经标成当班可用的，当场撤下；撤下原因写明就是本次校准没过。
            // 与校准记录同一事务，一提交两边一起生效；中途任何一步失败全部回滚。
            Optional<DutyRosterEntry> onDuty = rosterRepository
                    .findByDeviceIdAndDutyDateAndStatus(device.getId(), today, DutyRosterEntry.STATUS_ON_DUTY);
            if (onDuty.isPresent()) {
                removedEntry = dutyRosterService.removeForCalibration(onDuty.get(), saved);
            }
        }

        saveChangeLog(saved, removedEntry);
        return new CalibrationSaveResult(saved, removedEntry);
    }

    private CalibrationConflictException conflict(Device device, CalibrationRecord first) {
        if (first == null) {
            return new CalibrationConflictException(String.format(
                    "枪【%s】本当班日已有校准结论，先落地的登记为准，本次结论不予记录",
                    device.getDeviceCode()));
        }
        return new CalibrationConflictException(String.format(
                "枪【%s】本当班日的校准结论已由 %s 先登记为「%s」，先落地的为准，后到的结论不能修改或覆盖",
                device.getDeviceCode(), first.getCalibrator(), first.getResult()));
    }

    private void saveChangeLog(CalibrationRecord record, DutyRosterEntry removedEntry) {
        ChangeLog log = new ChangeLog();
        log.setDeviceId(record.getDeviceId());
        log.setDeviceCode(record.getDeviceCode());
        log.setTimeSlotId(null);
        log.setChangeType("体温枪校准");
        log.setBeforeValue("本当班日尚无校准结论");
        String after = "校准结论：" + record.getResult() + "（校准人：" + record.getCalibrator() + "）";
        if (RESULT_FAIL.equals(record.getResult()) && removedEntry != null) {
            after += "；已在当班可用名单当场撤下，撤下原因：" + removedEntry.getRemoveReason();
        } else if (RESULT_FAIL.equals(record.getResult())) {
            after += "；该枪本就不在当班可用名单中，当班日内不得再挂入";
        } else {
            after += "；当班可用名单状态不变";
        }
        log.setAfterValue(after);
        log.setOperator(record.getCalibrator());
        changeLogRepository.save(log);
    }
}
