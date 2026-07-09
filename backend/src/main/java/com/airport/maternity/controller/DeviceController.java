package com.airport.maternity.controller;

import com.airport.maternity.dto.DeviceDTO;
import com.airport.maternity.dto.ResponseDTO;
import com.airport.maternity.entity.Device;
import com.airport.maternity.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
@CrossOrigin(origins = "*")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

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
        return ResponseDTO.success(deviceService.save(dto));
    }

    @PutMapping("/{id}")
    public ResponseDTO<Device> update(@PathVariable Long id, @Valid @RequestBody DeviceDTO dto) {
        dto.setId(id);
        return ResponseDTO.success(deviceService.save(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> delete(@PathVariable Long id) {
        deviceService.deleteById(id);
        return ResponseDTO.success(null);
    }
}