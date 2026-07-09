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
import java.util.Optional;

@Service
public class TimeSlotService {

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private DeviceService deviceService;

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

        timeSlot.setDeviceId(dto.getDeviceId());
        timeSlot.setStartTime(LocalTime.parse(dto.getStartTime(), TIME_FORMATTER));
        timeSlot.setEndTime(LocalTime.parse(dto.getEndTime(), TIME_FORMATTER));
        timeSlot.setStartDate(LocalDate.parse(dto.getStartDate(), DATE_FORMATTER));
        timeSlot.setEndDate(LocalDate.parse(dto.getEndDate(), DATE_FORMATTER));
        timeSlot.setStatus(dto.getStatus());

        TimeSlot saved = timeSlotRepository.save(timeSlot);

        String afterValue = formatTimeSlot(saved);
        String changeType = dto.getId() != null ? "时段调整" : "时段绑定";
        
        saveChangeLog(saved.getDeviceId(), saved.getId(), changeType, beforeValue, afterValue);

        return saved;
    }

    @Transactional
    public void deleteById(Long id) {
        Optional<TimeSlot> timeSlot = timeSlotRepository.findById(id);
        if (timeSlot.isPresent()) {
            String beforeValue = formatTimeSlot(timeSlot.get());
            saveChangeLog(timeSlot.get().getDeviceId(), id, "时段解绑", beforeValue, null);
            timeSlotRepository.deleteById(id);
        }
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

    public List<Long> findDeviceIdsByTimeRange(String startTime, String endTime) {
        LocalTime start = LocalTime.parse(startTime, TIME_FORMATTER);
        LocalTime end = LocalTime.parse(endTime, TIME_FORMATTER);
        return timeSlotRepository.findDeviceIdsByTimeRange(start, end);
    }

    public List<TimeSlot> findByTimeRange(String startTime, String endTime) {
        LocalTime start = LocalTime.parse(startTime, TIME_FORMATTER);
        LocalTime end = LocalTime.parse(endTime, TIME_FORMATTER);
        return timeSlotRepository.findByTimeRange(start, end);
    }
}