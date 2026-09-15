package com.airport.maternity.controller;

import com.airport.maternity.dto.DeviceDTO;
import com.airport.maternity.dto.DeviceSaveResult;
import com.airport.maternity.dto.ResponseDTO;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.TimeSlot;
import com.airport.maternity.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/devices")
@CrossOrigin(origins = "*")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @GetMapping
    public ResponseDTO<Page<Device>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String terminalArea,
            @RequestParam(required = false) String deviceType) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Device> result = deviceService.findAll(terminalArea, deviceType, pageable);
        return ResponseDTO.success(result);
    }

    @GetMapping("/{id}")
    public ResponseDTO<Device> getById(@PathVariable Long id) {
        return deviceService.findById(id)
                .map(ResponseDTO::success)
                .orElse(ResponseDTO.error(404, "设备不存在"));
    }

    @PostMapping
    public ResponseDTO<Device> create(@Valid @RequestBody DeviceDTO dto) {
        return ResponseDTO.success(deviceService.save(dto).getDevice());
    }

    @PutMapping("/{id}")
    public ResponseDTO<Device> update(@PathVariable Long id, @Valid @RequestBody DeviceDTO dto) {
        dto.setId(id);
        DeviceSaveResult result = deviceService.save(dto);
        List<TimeSlot> invalidated = result.getInvalidatedSlots();
        if (!invalidated.isEmpty()) {
            String detail = invalidated.stream()
                    .map(s -> s.getStartDate().format(DATE_FORMATTER) + " ~ " + s.getEndDate().format(DATE_FORMATTER)
                            + " " + s.getStartTime().format(TIME_FORMATTER) + "-" + s.getEndTime().format(TIME_FORMATTER))
                    .collect(Collectors.joining("；"));
            return ResponseDTO.success(
                    "保存成功。新分区高峰名额不足，以下 " + invalidated.size() + " 段尚未开始的时段已置为失效：" + detail,
                    result.getDevice());
        }
        return ResponseDTO.success(result.getDevice());
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> delete(@PathVariable Long id) {
        deviceService.deleteById(id);
        return ResponseDTO.success(null);
    }
}
