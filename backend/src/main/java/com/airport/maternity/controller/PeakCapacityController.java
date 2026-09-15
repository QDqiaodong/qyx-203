package com.airport.maternity.controller;

import com.airport.maternity.dto.PeakCapacityLimitDTO;
import com.airport.maternity.dto.PeakCapacityStatusDTO;
import com.airport.maternity.dto.ResponseDTO;
import com.airport.maternity.entity.PeakCapacityLimit;
import com.airport.maternity.entity.PeakWindow;
import com.airport.maternity.service.PeakCapacityService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/peak-capacity")
@CrossOrigin(origins = "*")
public class PeakCapacityController {

    @Autowired
    private PeakCapacityService peakCapacityService;

    @GetMapping("/windows")
    public ResponseDTO<List<PeakWindow>> windows() {
        return ResponseDTO.success(peakCapacityService.getPeakWindows());
    }

    @GetMapping
    public ResponseDTO<List<PeakCapacityStatusDTO>> list() {
        return ResponseDTO.success(peakCapacityService.listLimitsWithUsage());
    }

    @PostMapping
    public ResponseDTO<PeakCapacityLimit> create(@Valid @RequestBody PeakCapacityLimitDTO dto) {
        return ResponseDTO.success(peakCapacityService.saveLimit(dto));
    }

    @PutMapping("/{id}")
    public ResponseDTO<PeakCapacityLimit> update(@PathVariable Long id, @Valid @RequestBody PeakCapacityLimitDTO dto) {
        dto.setId(id);
        return ResponseDTO.success(peakCapacityService.saveLimit(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> delete(@PathVariable Long id) {
        peakCapacityService.deleteLimit(id);
        return ResponseDTO.success(null);
    }
}
