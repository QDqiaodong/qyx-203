package com.airport.maternity.controller;

import com.airport.maternity.dto.ResponseDTO;
import com.airport.maternity.dto.TimeSlotDTO;
import com.airport.maternity.entity.TimeSlot;
import com.airport.maternity.service.TimeSlotService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/time-slots")
@CrossOrigin(origins = "*")
public class TimeSlotController {

    @Autowired
    private TimeSlotService timeSlotService;

    @GetMapping
    public ResponseDTO<Page<TimeSlot>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long deviceId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TimeSlot> result = timeSlotService.findAll(deviceId, pageable);
        return ResponseDTO.success(result);
    }

    @GetMapping("/{id}")
    public ResponseDTO<TimeSlot> getById(@PathVariable Long id) {
        return timeSlotService.findById(id)
                .map(ResponseDTO::success)
                .orElse(ResponseDTO.error(404, "时段绑定不存在"));
    }

    @GetMapping("/device/{deviceId}")
    public ResponseDTO<List<TimeSlot>> getByDeviceId(@PathVariable Long deviceId) {
        return ResponseDTO.success(timeSlotService.findByDeviceId(deviceId));
    }

    @PostMapping
    public ResponseDTO<TimeSlot> create(@Valid @RequestBody TimeSlotDTO dto) {
        return ResponseDTO.success(timeSlotService.save(dto));
    }

    @PutMapping("/{id}")
    public ResponseDTO<TimeSlot> update(@PathVariable Long id, @Valid @RequestBody TimeSlotDTO dto) {
        dto.setId(id);
        return ResponseDTO.success(timeSlotService.save(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> delete(@PathVariable Long id) {
        timeSlotService.deleteById(id);
        return ResponseDTO.success(null);
    }
}