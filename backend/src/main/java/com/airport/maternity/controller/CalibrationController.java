package com.airport.maternity.controller;

import com.airport.maternity.dto.CalibrationDTO;
import com.airport.maternity.dto.CalibrationSaveResult;
import com.airport.maternity.dto.ResponseDTO;
import com.airport.maternity.entity.CalibrationRecord;
import com.airport.maternity.entity.DutyRosterEntry;
import com.airport.maternity.service.CalibrationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 体温枪校准登记台账。
 */
@RestController
@RequestMapping("/api/calibrations")
@CrossOrigin(origins = "*")
public class CalibrationController {

    @Autowired
    private CalibrationService calibrationService;

    /** 校准登记台账：可按枪号 / 当班日筛选 */
    @GetMapping
    public ResponseDTO<Page<CalibrationRecord>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String dutyDate) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        LocalDate date = (dutyDate != null && !dutyDate.isEmpty()) ? LocalDate.parse(dutyDate) : null;
        return ResponseDTO.success(calibrationService.findAll(deviceId, date, pageable));
    }

    /**
     * 登记校准：选中枪号、写下校准人、结论通过/不通过。
     * 不通过时当班名单中这把枪已在同一事务内当场撤下；
     * 同枪同日后到的第二份结论返回 409，先落地的为准。
     */
    @PostMapping
    public ResponseDTO<CalibrationRecord> create(@Valid @RequestBody CalibrationDTO dto) {
        CalibrationSaveResult result = calibrationService.save(dto);
        if (CalibrationService.RESULT_FAIL.equals(dto.getResult())) {
            DutyRosterEntry removed = result.getRemovedRosterEntry();
            if (removed != null) {
                return ResponseDTO.success(String.format(
                        "校准结论已登记为「不通过」（校准人：%s）。枪【%s】已从当班可用名单当场撤下，撤下原因：%s。"
                                + "本当班日内不能再列入当班名单。",
                        dto.getCalibrator(), removed.getDeviceCode(), removed.getRemoveReason()),
                        result.getCalibration());
            }
            return ResponseDTO.success(String.format(
                    "校准结论已登记为「不通过」（校准人：%s）。该枪不在当班可用名单中，本当班日内不得列入。",
                    dto.getCalibrator()), result.getCalibration());
        }
        return ResponseDTO.success(
                "校准结论已登记为「通过」，当班可用名单状态不变。", result.getCalibration());
    }
}
