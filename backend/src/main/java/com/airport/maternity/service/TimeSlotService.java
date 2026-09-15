package com.airport.maternity.service;

import com.airport.maternity.dto.TimeSlotDTO;
import com.airport.maternity.entity.ChangeLog;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.TimeSlot;
import com.airport.maternity.repository.ChangeLogRepository;
import com.airport.maternity.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TimeSlotService {

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private PeakCapacityService peakCapacityService;

    @Autowired
    private ChangeLogRepository changeLogRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Page<TimeSlot> findAll(Long deviceId, Pageable pageable) {
        if (deviceId != null) {
            return timeSlotRepository.findByDeviceId(deviceId, pageable);
        }
        return timeSlotRepository.findAll(pageable);
    }

    public Optional<TimeSlot> findById(Long id) {
        return timeSlotRepository.findById(id);
    }

    public List<TimeSlot> findByDeviceId(Long deviceId) {
        return timeSlotRepository.findByDeviceId(deviceId);
    }

    @Transactional
    public TimeSlot save(TimeSlotDTO dto) {
        TimeSlot timeSlot;
        String beforeValue = null;

        if (dto.getId() != null) {
            Optional<TimeSlot> existing = timeSlotRepository.findById(dto.getId());
            if (existing.isPresent()) {
                timeSlot = existing.get();
                beforeValue = formatTimeSlot(timeSlot);
            } else {
                timeSlot = new TimeSlot();
            }
        } else {
            timeSlot = new TimeSlot();
        }

        LocalTime startTime = parseTime(dto.getStartTime());
        LocalTime endTime = parseTime(dto.getEndTime());
        LocalDate startDate = LocalDate.parse(dto.getStartDate(), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(dto.getEndDate(), DATE_FORMATTER);
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }

        // 锁设备行：与设备停用/删除互斥，避免一边时段写成生效中、一边设备已停用或没了。
        // 换绑设备时原设备行也要锁；多设备按 id 升序加锁，保证全局锁序一致、不死锁。
        Long oldDeviceId = timeSlot.getId() != null ? timeSlot.getDeviceId() : null;
        List<Long> deviceIdsToLock = Stream.of(oldDeviceId, dto.getDeviceId())
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        Device device = null;
        for (Long lockId : deviceIdsToLock) {
            Device lockedDevice = deviceService.lockById(lockId);
            if (lockId.equals(dto.getDeviceId())) {
                device = lockedDevice;
            }
        }

        // 停用设备名下不允许再挂生效中占用（停用瞬间存量占用已被置为失效）
        if (PeakCapacityService.STATUS_ACTIVE.equals(dto.getStatus())
                && DeviceService.DEVICE_STATUS_DISABLED.equals(device.getStatus())) {
            throw new IllegalArgumentException("设备【" + device.getDeviceCode() + "】已停用，不能保存「生效中」时段");
        }

        timeSlot.setDeviceId(dto.getDeviceId());
        timeSlot.setStartTime(startTime);
        timeSlot.setEndTime(endTime);
        timeSlot.setStartDate(startDate);
        timeSlot.setEndDate(endDate);
        timeSlot.setStatus(dto.getStatus());

        // 生效中的时段若在高峰窗内把该分区该类型的同时在用数顶过上限，本次保存直接拦截
        if (PeakCapacityService.STATUS_ACTIVE.equals(dto.getStatus())) {
            peakCapacityService.assertWithinCapacity(device, timeSlot, timeSlot.getId());
        }

        TimeSlot saved = timeSlotRepository.save(timeSlot);

        String afterValue = formatTimeSlot(saved);
        String changeType = dto.getId() != null ? "时段调整" : "时段绑定";

        saveChangeLog(saved.getDeviceId(), device.getDeviceCode(), saved.getId(), changeType, beforeValue, afterValue);

        return saved;
    }

    @Transactional
    public void deleteById(Long id) {
        Optional<TimeSlot> timeSlot = timeSlotRepository.findById(id);
        if (timeSlot.isPresent()) {
            // 与设备停用/删除共用设备行锁，禁止并发时留下对不上的状态
            Device device = deviceService.lockById(timeSlot.get().getDeviceId());
            TimeSlot locked = timeSlotRepository.findByIdForUpdate(id).orElse(timeSlot.get());
            String beforeValue = formatTimeSlot(locked);
            saveChangeLog(locked.getDeviceId(), device.getDeviceCode(), id, "时段解绑", beforeValue, null);
            timeSlotRepository.deleteById(id);
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("时间不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() == 5) {
            return LocalTime.parse(trimmed, TIME_FORMATTER);
        }
        return LocalTime.parse(trimmed);
    }

    private String formatTimeSlot(TimeSlot timeSlot) {
        return String.format("%s %s-%s",
            timeSlot.getStartDate().format(DATE_FORMATTER) + " ~ " + timeSlot.getEndDate().format(DATE_FORMATTER),
            timeSlot.getStartTime().format(TIME_FORMATTER),
            timeSlot.getEndTime().format(TIME_FORMATTER));
    }

    private void saveChangeLog(Long deviceId, String deviceCode, Long timeSlotId, String changeType, String beforeValue, String afterValue) {
        ChangeLog log = new ChangeLog();
        log.setDeviceId(deviceId);
        log.setDeviceCode(deviceCode);
        log.setTimeSlotId(timeSlotId);
        log.setChangeType(changeType);
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        log.setOperator("admin");
        changeLogRepository.save(log);
    }

    /**
     * 统计页「按时段筛选」：只取正常在用设备名下、生效中、查询日在日期范围内
     * 且每日时段相交的占用。停用设备、已失效/已停用时段、历史时段均不命中。
     */
    public List<Long> findInUseDeviceIdsByTimeRange(String startTime, String endTime) {
        LocalTime start = LocalTime.parse(startTime, TIME_FORMATTER);
        LocalTime end = LocalTime.parse(endTime, TIME_FORMATTER);
        return timeSlotRepository.findInUseDeviceIds(start, end, LocalDate.now());
    }

    public List<TimeSlot> findByTimeRange(String startTime, String endTime) {
        LocalTime start = LocalTime.parse(startTime, TIME_FORMATTER);
        LocalTime end = LocalTime.parse(endTime, TIME_FORMATTER);
        return timeSlotRepository.findByTimeRange(start, end);
    }
}
