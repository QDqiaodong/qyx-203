package com.airport.maternity.controller;

import com.airport.maternity.dto.ResponseDTO;
import com.airport.maternity.entity.ChangeLog;
import com.airport.maternity.service.ChangeLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/change-logs")
@CrossOrigin(origins = "*")
public class ChangeLogController {

    @Autowired
    private ChangeLogService changeLogService;

    @GetMapping
    public ResponseDTO<Page<ChangeLog>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "changeTime"));
        Page<ChangeLog> result = changeLogService.findAll(deviceId, startDate, endDate, pageable);
        return ResponseDTO.success(result);
    }
}