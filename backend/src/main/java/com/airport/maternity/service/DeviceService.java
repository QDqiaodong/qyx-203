package com.airport.maternity.service;

import com.airport.maternity.dto.DeviceDTO;
import com.airport.maternity.dto.DeviceDeleteResult;
import com.airport.maternity.dto.DeviceSaveResult;
import com.airport.maternity.entity.ChangeLog;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.TimeSlot;
import com.airport.maternity.exception.DeviceInUseException;
import com.airport.maternity.repository.ChangeLogRepository;
import com.airport.maternity.repository.DeviceRepository;
import com.airport.maternity.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DeviceService {

    public static final String DEVICE_STATUS_NORMAL = "正常";
    public static final String DEVICE_STATUS_DISABLED = "停用";

    /** 设备停用/删除联动失效的变更原因，随保存结果返回用于接口提示 */
    public static final String REASON_DISABLED = "设备停用";
    /** 调区高峰超员导致的时段失效，随保存结果返回用于接口提示 */
    public static final String REASON_MOVED = "设备调区";

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private PeakCapacityService peakCapacityService;

    @Autowired
    private ChangeLogRepository changeLogRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Page<Device> findAll(String terminalArea, String deviceType, Pageable pageable) {
        if (terminalArea != null && !terminalArea.isEmpty() && deviceType != null && !deviceType.isEmpty()) {
            return deviceRepository.findByTerminalAreaAndDeviceType(terminalArea, deviceType, pageable);
        } else if (terminalArea != null && !terminalArea.isEmpty()) {
            return deviceRepository.findByTerminalArea(terminalArea, pageable);
        } else if (deviceType != null && !deviceType.isEmpty()) {
            return deviceRepository.findByDeviceType(deviceType, pageable);
        }
        return deviceRepository.findAll(pageable);
    }

    public Optional<Device> findById(Long id) {
        return deviceRepository.findById(id);
    }

    public Optional<Device> findByDeviceCode(String deviceCode) {
        return deviceRepository.findByDeviceCode(deviceCode);
    }

    /**
     * 锁住设备档案行：停用/删除与时段编辑并发时串行化，
     * 不会出现一边时段写成生效中、一边设备已停用/删除。
     */
    public Device lockById(Long id) {
        return deviceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
    }

    @Transactional
    public DeviceSaveResult save(DeviceDTO dto) {
        Device device;
        boolean isExisting = dto.getId() != null;
        String oldArea = null;
        String oldType = null;
        String oldStatus = null;
        if (isExisting) {
            // 编辑走悲观锁，与并发的时段编辑/设备删除互斥
            device = lockById(dto.getId());
            oldArea = device.getTerminalArea();
            oldType = device.getDeviceType();
            oldStatus = device.getStatus();
        } else {
            device = new Device();
        }

        // 分区或类型变化都会改变该设备全部时段所属的「分区+类型」高峰配额桶
        boolean bucketChanged = isExisting
                && (!Objects.equals(oldArea, dto.getTerminalArea()) || !Objects.equals(oldType, dto.getDeviceType()));
        // 本次保存是否把设备从非停用改成停用（停用→停用、停用→其他不联动）
        boolean disabledNow = isExisting
                && DEVICE_STATUS_DISABLED.equals(dto.getStatus())
                && !DEVICE_STATUS_DISABLED.equals(oldStatus);

        device.setDeviceCode(dto.getDeviceCode());
        device.setDeviceType(dto.getDeviceType());
        device.setTerminalArea(dto.getTerminalArea());
        device.setStatus(dto.getStatus());
        Device saved = deviceRepository.save(device);

        List<TimeSlot> invalidated = List.of();
        String reason = null;

        if (bucketChanged) {
            // 调区/改类型：已经开始的时段保持生效且不占新分区名额，
            // 尚未开始且会超员的时段置为失效，已结束的历史时段不动
            invalidated = peakCapacityService.reconcileDeviceMove(saved, dto.getTerminalArea(), dto.getDeviceType());
            saveChangeLog(saved.getId(), null, "设备调区",
                    oldArea + " / " + oldType,
                    dto.getTerminalArea() + " / " + dto.getDeviceType());
            for (TimeSlot slot : invalidated) {
                saveChangeLog(saved.getId(), slot.getId(), "时段失效",
                        formatTimeSlot(slot) + "（生效中）",
                        "已失效（" + dto.getTerminalArea() + " · " + dto.getDeviceType() + " 高峰超员）");
            }
            reason = REASON_MOVED;
        }

        if (disabledNow) {
            // 停用：名下尚未结束（进行中 + 未开始）的生效中占用全部置为失效，
            // 不能再按正常在用出现在时段列表和统计里；已结束的历史时段不动
            invalidated = invalidateOpenSlots(saved, REASON_DISABLED);
            saveChangeLog(saved.getId(), null, "设备停用",
                    oldStatus + "（名下生效中占用）",
                    "停用；名下 " + invalidated.size() + " 段尚未结束的占用已置为失效");
            reason = REASON_DISABLED;
        }

        return new DeviceSaveResult(saved, invalidated, reason);
    }

    /**
     * 删除设备档案。
     * 默认（force=false）：若名下还有进行中的生效中占用（现场可能还在用），
     * 直接拦截并点名是哪几段；force=true 时允许删除，但必须先把未结束占用
     * 全部置为「已失效」并逐段写变更，再在同一事务内清掉设备与全部时段行，
     * 不允许留下挂在设备编号上的孤儿时段。
     */
    @Transactional
    public DeviceDeleteResult deleteById(Long id, boolean force) {
        Device device = deviceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
        // 锁住名下全部时段行，与并发时段编辑互斥
        List<TimeSlot> slots = timeSlotRepository.findByDeviceIdForUpdate(id);
        LocalDate today = LocalDate.now();

        List<TimeSlot> activeOpen = slots.stream()
                .filter(s -> PeakCapacityService.STATUS_ACTIVE.equals(s.getStatus()))
                .filter(s -> !s.getEndDate().isBefore(today))
                .sorted(Comparator.comparing(TimeSlot::getStartDate).thenComparing(TimeSlot::getStartTime))
                .collect(Collectors.toList());
        List<TimeSlot> ongoing = activeOpen.stream()
                .filter(s -> !s.getStartDate().isAfter(today))
                .collect(Collectors.toList());

        if (!ongoing.isEmpty() && !force) {
            String detail = ongoing.stream().map(this::formatTimeSlot).collect(Collectors.joining("；"));
            throw new DeviceInUseException(String.format(
                    "设备【%s】有 %d 段占用正在进行中，现场可能仍在使用，删除已被拦截：%s。"
                            + "请待其结束或先停用设备后再删；如确认现在移除，请勾选「知晓进行中占用，强制删除」"
                            + "——系统会先把全部未结束占用置为失效并写入变更记录，再删除设备与其名下时段。",
                    device.getDeviceCode(), ongoing.size(), detail));
        }

        // 未结束占用先置失效并逐段留痕（进行中点名「进行中」，未开始点名「未开始」）
        for (TimeSlot slot : activeOpen) {
            String phase = !slot.getStartDate().isAfter(today) ? "进行中" : "未开始";
            String before = formatTimeSlot(slot) + "（生效中·" + phase + "）";
            slot.setStatus(PeakCapacityService.STATUS_INVALIDATED);
            timeSlotRepository.save(slot);
            saveChangeLog(id, slot.getId(), "时段失效", before,
                    "已失效（设备删除：" + device.getDeviceCode() + "）");
        }

        // 一条汇总记录，保证变更记录能对上这次删除带走了哪些占用
        saveChangeLog(id, null, "设备删除",
                String.format("设备编号 %s（%s · %s）；名下共 %d 段时段，其中 %d 段未结束占用已置为失效",
                        device.getDeviceCode(), device.getTerminalArea(), device.getDeviceType(),
                        slots.size(), activeOpen.size()),
                null);

        // 同一事务清除全部时段行与设备行，不留孤儿时段
        timeSlotRepository.deleteAllInBatch(slots);
        deviceRepository.delete(device);

        return new DeviceDeleteResult(slots.size(), activeOpen.size(), ongoing.size(), !ongoing.isEmpty());
    }

    /**
     * 把设备名下尚未结束（endDate >= 今天）的生效中时段置为失效并逐段写变更，
     * 已结束的历史时段保持原样。
     */
    private List<TimeSlot> invalidateOpenSlots(Device device, String reason) {
        LocalDate today = LocalDate.now();
        List<TimeSlot> slots = timeSlotRepository.findByDeviceIdForUpdate(device.getId());
        List<TimeSlot> openSlots = slots.stream()
                .filter(s -> PeakCapacityService.STATUS_ACTIVE.equals(s.getStatus()))
                .filter(s -> !s.getEndDate().isBefore(today))
                .sorted(Comparator.comparing(TimeSlot::getStartDate).thenComparing(TimeSlot::getStartTime))
                .collect(Collectors.toList());
        for (TimeSlot slot : openSlots) {
            String phase = !slot.getStartDate().isAfter(today) ? "进行中" : "未开始";
            String before = formatTimeSlot(slot) + "（生效中·" + phase + "）";
            slot.setStatus(PeakCapacityService.STATUS_INVALIDATED);
            timeSlotRepository.save(slot);
            saveChangeLog(device.getId(), slot.getId(), "时段失效", before,
                    "已失效（" + reason + "）");
        }
        return openSlots;
    }

    public List<Device> findAllDevices() {
        return deviceRepository.findAll();
    }

    private String formatTimeSlot(TimeSlot timeSlot) {
        return String.format("%s %s-%s",
                timeSlot.getStartDate().format(DATE_FORMATTER) + " ~ " + timeSlot.getEndDate().format(DATE_FORMATTER),
                timeSlot.getStartTime().format(TIME_FORMATTER),
                timeSlot.getEndTime().format(TIME_FORMATTER));
    }

    private void saveChangeLog(Long deviceId, Long timeSlotId, String changeType, String beforeValue, String afterValue) {
        ChangeLog log = new ChangeLog();
        log.setDeviceId(deviceId);
        // 设备行在同事务内尚未删除，取编号快照；删除后记录仍能对上台账编号
        log.setDeviceCode(deviceRepository.findById(deviceId).map(Device::getDeviceCode).orElse(null));
        log.setTimeSlotId(timeSlotId);
        log.setChangeType(changeType);
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        log.setOperator("admin");
        changeLogRepository.save(log);
    }
}
