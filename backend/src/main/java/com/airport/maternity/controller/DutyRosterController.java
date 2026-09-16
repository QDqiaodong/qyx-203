package com.airport.maternity.controller;

import com.airport.maternity.dto.DutyRosterAddDTO;
import com.airport.maternity.dto.ResponseDTO;
import com.airport.maternity.entity.DutyRosterEntry;
import com.airport.maternity.service.DutyRosterService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 当班可用名单：值机手头当天可以当班使用的体温枪。
 */
@RestController
@RequestMapping("/api/duty-roster")
@CrossOrigin(origins = "*")
public class DutyRosterController {

    @Autowired
    private DutyRosterService dutyRosterService;

    /**
     * 当班可用名单。all=true 连同「已撤下」留痕一起返回；
     * 默认只返回当班可用（校准没过的枪绝不出现）。
     */
    @GetMapping
    public ResponseDTO<List<DutyRosterEntry>> list(
            @RequestParam(required = false) String dutyDate,
            @RequestParam(defaultValue = "false") boolean all) {
        LocalDate date = (dutyDate != null && !dutyDate.isEmpty()) ? LocalDate.parse(dutyDate) : LocalDate.now();
        return ResponseDTO.success(dutyRosterService.findByDutyDate(date, !all));
    }

    /** 把枪列入当天当班可用名单（当天校准不通过 / 设备停用会被拦下） */
    @PostMapping
    public ResponseDTO<DutyRosterEntry> add(@Valid @RequestBody DutyRosterAddDTO dto) {
        DutyRosterEntry entry = dutyRosterService.addToRoster(dto);
        return ResponseDTO.success("枪【" + entry.getDeviceCode() + "】已列入当班可用名单", entry);
    }

    /** 现场手工撤下（非校准原因） */
    @PutMapping("/{id}/remove")
    public ResponseDTO<DutyRosterEntry> remove(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        DutyRosterEntry entry = dutyRosterService.removeManually(id, reason);
        return ResponseDTO.success("枪【" + entry.getDeviceCode() + "】已从当班可用名单撤下", entry);
    }
}
