package com.airport.maternity.service;

import com.airport.maternity.dto.DeviceDTO;
import com.airport.maternity.dto.DeviceSaveResult;
import com.airport.maternity.entity.ChangeLog;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.TimeSlot;
import com.airport.maternity.repository.ChangeLogRepository;
import com.airport.maternity.repository.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class DeviceService {

    @Autowired
    private DeviceRepository deviceRepository;

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

    @Transactional
    public DeviceSaveResult save(DeviceDTO dto) {
        Device device = new Device();
        if (dto.getId() != null) {
            device = deviceRepository.findById(dto.getId()).orElse(new Device());
        }

        boolean isExisting = device.getId() != null;
        String oldArea = device.getTerminalArea();
        String oldType = device.getDeviceType();
        // 分区或类型变化都会改变该设备全部时段所属的「分区+类型」高峰配额桶
        boolean bucketChanged = isExisting
                && (!Objects.equals(oldArea, dto.getTerminalArea()) || !Objects.equals(oldType, dto.getDeviceType()));

        device.setDeviceCode(dto.getDeviceCode());
        device.setDeviceType(dto.getDeviceType());
        device.setTerminalArea(dto.getTerminalArea());
        device.setStatus(dto.getStatus());
        Device saved = deviceRepository.save(device);

        List<TimeSlot> invalidated = List.of();
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
        }
        return new DeviceSaveResult(saved, invalidated);
    }

    @Transactional
    public void deleteById(Long id) {
        deviceRepository.deleteById(id);
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
        log.setTimeSlotId(timeSlotId);
        log.setChangeType(changeType);
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        log.setOperator("admin");
        changeLogRepository.save(log);
    }
}
