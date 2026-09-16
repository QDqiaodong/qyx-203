package com.airport.maternity.service;

import com.airport.maternity.dto.DutyRosterAddDTO;
import com.airport.maternity.entity.CalibrationRecord;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.DutyRosterEntry;
import com.airport.maternity.exception.CalibrationNotPassedException;
import com.airport.maternity.repository.CalibrationRecordRepository;
import com.airport.maternity.repository.DutyRosterEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 当班可用名单：当天值机手头可以当班使用的体温枪。
 *
 * 与校准登记共用设备行悲观锁 + 名单行悲观锁：
 * 校准不通过撤名单、往名单挂枪并发时串行化，
 * 不会出现「校准已经判不通过、枪又被挂成当班可用」。
 */
@Service
public class DutyRosterService {

    @Autowired
    private DutyRosterEntryRepository rosterRepository;

    @Autowired
    private CalibrationRecordRepository calibrationRepository;

    @Autowired
    private DeviceService deviceService;

    /**
     * 当班日名单。onlyOnDuty=true 只返回仍当班可用的（校准没过被撤下的不出现）；
     * false 时连同撤下留痕一起返回。
     */
    public List<DutyRosterEntry> findByDutyDate(LocalDate dutyDate, boolean onlyOnDuty) {
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        if (onlyOnDuty) {
            return rosterRepository.findByDutyDateAndStatus(dutyDate, DutyRosterEntry.STATUS_ON_DUTY, sort);
        }
        return rosterRepository.findByDutyDate(dutyDate, sort);
    }

    /**
     * 把枪列入当天当班可用名单。
     * 已停用设备不能当班；当天校准结论为「不通过」的枪一律拦下——
     * 不是页面上加个勾选就算过，挂名单这一步直接不允许。
     */
    @Transactional
    public DutyRosterEntry addToRoster(DutyRosterAddDTO dto) {
        // 锁设备行：与校准登记互斥
        Device device = deviceService.lockById(dto.getDeviceId());
        LocalDate today = LocalDate.now();

        if (DeviceService.DEVICE_STATUS_DISABLED.equals(device.getStatus())) {
            throw new IllegalArgumentException(
                    "设备【" + device.getDeviceCode() + "】已停用，不能列入当班可用名单");
        }

        Optional<DutyRosterEntry> existing = rosterRepository
                .findByDeviceIdAndDutyDateAndStatus(device.getId(), today, DutyRosterEntry.STATUS_ON_DUTY);
        if (existing.isPresent()) {
            throw new IllegalArgumentException(
                    "枪【" + device.getDeviceCode() + "】已经在今天的当班可用名单里，不能重复挂入");
        }

        assertCalibrationAllowsDuty(device, today);

        DutyRosterEntry entry = new DutyRosterEntry();
        entry.setDeviceId(device.getId());
        entry.setDeviceCode(device.getDeviceCode());
        entry.setDutyDate(today);
        entry.setStatus(DutyRosterEntry.STATUS_ON_DUTY);
        return rosterRepository.save(entry);
    }

    /**
     * 当班可用名单闸口：当天校准结论必须不是「不通过」。
     * 调用方已持有设备行悲观锁，与校准登记互斥。
     */
    public void assertCalibrationAllowsDuty(Device device, LocalDate dutyDate) {
        calibrationRepository.findByDeviceIdAndDutyDate(device.getId(), dutyDate)
                .filter(c -> CalibrationService.RESULT_FAIL.equals(c.getResult()))
                .ifPresent(c -> {
                    throw new CalibrationNotPassedException(String.format(
                            "枪【%s】今天的校准结论为「不通过」（校准人：%s），不能列入当班可用名单；"
                                    + "本次当班日内该枪不得再当班。",
                            device.getDeviceCode(), c.getCalibrator()));
                });
    }

    /**
     * 手工撤下当班名单行（与校准无关的现场撤换）。
     * 校准不通过的撤下由 CalibrationService 在同一事务内调用
     * {@link #removeForCalibration} 完成，保证记录与撤下同生共死。
     */
    @Transactional
    public DutyRosterEntry removeManually(Long entryId, String reason) {
        DutyRosterEntry entry = rosterRepository.findByIdForUpdate(entryId)
                .orElseThrow(() -> new IllegalArgumentException("名单行不存在"));
        if (DutyRosterEntry.STATUS_REMOVED.equals(entry.getStatus())) {
            throw new IllegalArgumentException("该枪已经不在当班可用名单里");
        }
        String detail = (reason == null || reason.trim().isEmpty())
                ? "现场手工撤下" : reason.trim();
        entry.setStatus(DutyRosterEntry.STATUS_REMOVED);
        entry.setRemoveReason(detail);
        return rosterRepository.save(entry);
    }

    /**
     * 校准结论为「不通过」时，在同一校准事务内当场撤下当班名单。
     * 调用方（CalibrationService）持有设备行锁且校准记录已落库：
     * 名单行置「已撤下」，撤下原因写明就是本次校准没过，并关联校准记录。
     */
    DutyRosterEntry removeForCalibration(DutyRosterEntry entry, CalibrationRecord calibration) {
        DutyRosterEntry locked = rosterRepository.findByIdForUpdate(entry.getId()).orElse(entry);
        locked.setStatus(DutyRosterEntry.STATUS_REMOVED);
        locked.setCalibrationId(calibration.getId());
        String detail = DutyRosterEntry.REMOVE_REASON_CALIBRATION
                + "：校准人 " + calibration.getCalibrator() + " 判定不通过";
        if (calibration.getRemark() != null && !calibration.getRemark().trim().isEmpty()) {
            detail += "（" + calibration.getRemark().trim() + "）";
        }
        locked.setRemoveReason(detail);
        return rosterRepository.save(locked);
    }
}
