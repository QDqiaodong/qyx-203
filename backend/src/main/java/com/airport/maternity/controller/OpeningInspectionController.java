package com.airport.maternity.controller;

import com.airport.maternity.dto.OpeningInspectionDTO;
import com.airport.maternity.dto.OpeningInspectionSaveResult;
import com.airport.maternity.dto.ResponseDTO;
import com.airport.maternity.entity.OpeningInspection;
import com.airport.maternity.entity.TimeSlot;
import com.airport.maternity.service.OpeningInspectionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inspections")
@CrossOrigin(origins = "*")
public class OpeningInspectionController {

    @Autowired
    private OpeningInspectionService inspectionService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @GetMapping
    public ResponseDTO<Page<OpeningInspection>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String date) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        LocalDate inspectionDate = (date != null && !date.isEmpty()) ? LocalDate.parse(date) : null;
        return ResponseDTO.success(inspectionService.findAll(deviceId, inspectionDate, pageable));
    }

    /**
     * 今日各设备最近一次巡检结论。
     * 设备列表「今日巡检」列、时段表单的巡检提示都从这里取，
     * 与加绑拦截共用同一份台账数据，关页再开后口径一致。
     */
    @GetMapping("/status/today")
    public ResponseDTO<Map<Long, OpeningInspection>> todayStatus() {
        return ResponseDTO.success(inspectionService.latestTodayByDevice());
    }

    @PostMapping
    public ResponseDTO<OpeningInspection> create(@Valid @RequestBody OpeningInspectionDTO dto) {
        OpeningInspectionSaveResult result = inspectionService.save(dto);
        if (OpeningInspectionService.RESULT_FAIL.equals(dto.getResult())) {
            List<TimeSlot> ongoing = result.getOngoingSlots();
            if (!ongoing.isEmpty()) {
                // 补检不通过且现场还在用：进行中占用继续有效、不按失效处理，逐段点名；
                // 只禁止再新挂「生效中」时段
                String detail = ongoing.stream()
                        .map(s -> String.format("%s ~ %s %s-%s",
                                s.getStartDate(), s.getEndDate(),
                                s.getStartTime().format(TIME_FORMATTER), s.getEndTime().format(TIME_FORMATTER)))
                        .collect(Collectors.joining("；"));
                return ResponseDTO.success(String.format(
                        "巡检已登记为「不通过」。该设备有 %d 段占用正在进行中，继续有效、不按失效处理：%s。"
                                + "自本次巡检起，今天补检「通过」前不能再给它新挂「生效中」时段。",
                        ongoing.size(), detail), result.getInspection());
            }
            return ResponseDTO.success("巡检已登记为「不通过」。今天补检「通过」前，不能再给该设备新挂「生效中」时段。",
                    result.getInspection());
        }
        return ResponseDTO.success(result.getInspection());
    }
}
