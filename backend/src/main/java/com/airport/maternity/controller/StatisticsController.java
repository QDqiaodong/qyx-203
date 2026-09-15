package com.airport.maternity.controller;

import com.airport.maternity.dto.ResponseDTO;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.TimeSlot;
import com.airport.maternity.service.DeviceService;
import com.airport.maternity.service.TimeSlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/statistics")
@CrossOrigin(origins = "*")
public class StatisticsController {

    @Autowired
    private TimeSlotService timeSlotService;

    @Autowired
    private DeviceService deviceService;

    @GetMapping("/by-time")
    public ResponseDTO<List<Device>> findByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        List<Long> deviceIds = timeSlotService.findInUseDeviceIdsByTimeRange(startTime, endTime);
        List<Device> devices = deviceIds.stream()
                .map(deviceService::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .collect(Collectors.toList());
        return ResponseDTO.success(devices);
    }

    @GetMapping("/device-detail/{deviceId}")
    public ResponseDTO<Map<String, Object>> getDeviceDetail(@PathVariable Long deviceId) {
        Map<String, Object> result = new HashMap<>();
        deviceService.findById(deviceId).ifPresent(device -> result.put("device", device));
        result.put("timeSlots", timeSlotService.findByDeviceId(deviceId));
        return ResponseDTO.success(result);
    }
}