package com.airport.maternity.service;

import com.airport.maternity.entity.ChangeLog;
import com.airport.maternity.repository.ChangeLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class ChangeLogService {

    @Autowired
    private ChangeLogRepository changeLogRepository;

    public Page<ChangeLog> findAll(Long deviceId, String startDate, String endDate, Pageable pageable) {
        if (deviceId != null && startDate != null && endDate != null) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            return changeLogRepository.findByDeviceIdAndChangeTimeBetween(deviceId, start, end, pageable);
        } else if (startDate != null && endDate != null) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            return changeLogRepository.findByChangeTimeBetween(start, end, pageable);
        } else if (deviceId != null) {
            return changeLogRepository.findByDeviceId(deviceId, pageable);
        }
        return changeLogRepository.findAll(pageable);
    }
}