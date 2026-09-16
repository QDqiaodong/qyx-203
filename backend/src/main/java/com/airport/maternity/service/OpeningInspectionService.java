package com.airport.maternity.service;

import com.airport.maternity.dto.OpeningInspectionDTO;
import com.airport.maternity.dto.OpeningInspectionSaveResult;
import com.airport.maternity.entity.ChangeLog;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.OpeningInspection;
import com.airport.maternity.entity.TimeSlot;
import com.airport.maternity.exception.InspectionNotPassedException;
import com.airport.maternity.repository.ChangeLogRepository;
import com.airport.maternity.repository.OpeningInspectionRepository;
import com.airport.maternity.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 开班巡检台账：
 * 早班保洁对每台母婴设备做开班巡检并登记（哪台设备、哪个分区、谁检的、检完是否缺巾缺纸）。
 * 当天最近一次巡检结论不是「通过」时，禁止给该设备新挂「生效中」时段；
 * 补检「不通过」时，进行中的占用不置失效、继续有效，只禁止再新挂。
 */
@Service
public class OpeningInspectionService {

    public static final String RESULT_PASS = "通过";
    public static final String RESULT_FAIL = "不通过";

    @Autowired
    private OpeningInspectionRepository inspectionRepository;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private ChangeLogRepository changeLogRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Page<OpeningInspection> findAll(Long deviceId, LocalDate date, Pageable pageable) {
        if (deviceId != null && date != null) {
            return inspectionRepository.findByDeviceIdAndInspectionDate(deviceId, date, pageable);
        } else if (deviceId != null) {
            return inspectionRepository.findByDeviceId(deviceId, pageable);
        } else if (date != null) {
            return inspectionRepository.findByInspectionDate(date, pageable);
        }
        return inspectionRepository.findAll(pageable);
    }

    /** 当天各设备最近一次巡检结论（设备列表「今日巡检」列与时段表单提示共用的数据源） */
    public Map<Long, OpeningInspection> latestTodayByDevice() {
        Map<Long, OpeningInspection> latest = new HashMap<>();
        for (OpeningInspection inspection : inspectionRepository.findByInspectionDate(LocalDate.now())) {
            latest.merge(inspection.getDeviceId(), inspection,
                    (a, b) -> a.getId() > b.getId() ? a : b);
        }
        return latest;
    }

    public Optional<OpeningInspection> latestToday(Long deviceId) {
        return inspectionRepository.findTopByDeviceIdAndInspectionDateOrderByIdDesc(deviceId, LocalDate.now());
    }

    /**
     * 登记开班巡检。与时段加绑/设备停用删除共用设备行悲观锁：
     * 一边补巡检一边给这台加绑时串行化，不会出现「巡检还是未通过、时段已写成生效中」。
     * 补检「不通过」且名下有进行中占用时：占用不置失效、继续有效，
     * 但自本次巡检起，当天补回「通过」前禁止再新挂「生效中」时段。
     */
    @Transactional
    public OpeningInspectionSaveResult save(OpeningInspectionDTO dto) {
        if (!RESULT_PASS.equals(dto.getResult()) && !RESULT_FAIL.equals(dto.getResult())) {
            throw new IllegalArgumentException("巡检结论只能是「通过」或「不通过」");
        }
        // 锁设备行：与时段保存/设备停用删除互斥
        Device device = deviceService.lockById(dto.getDeviceId());
        LocalDate today = LocalDate.now();
        Optional<OpeningInspection> previous = latestToday(device.getId());

        OpeningInspection inspection = new OpeningInspection();
        inspection.setDeviceId(device.getId());
        // 编号与分区随巡检落库：设备调区/删除后，台账仍能对上当时是哪台哪个分区
        inspection.setDeviceCode(device.getDeviceCode());
        inspection.setTerminalArea(device.getTerminalArea());
        inspection.setInspector(dto.getInspector());
        inspection.setResult(dto.getResult());
        inspection.setTowelShortage(Boolean.TRUE.equals(dto.getTowelShortage()));
        inspection.setPaperShortage(Boolean.TRUE.equals(dto.getPaperShortage()));
        inspection.setRemark(dto.getRemark());
        inspection.setInspectionDate(today);
        OpeningInspection saved = inspectionRepository.save(inspection);

        // 补检「不通过」：已经开始的占用不置失效、继续有效（不能一律改失效就算过），
        // 只禁止再新挂；进行中占用逐段点名，随结果与变更记录留痕
        List<TimeSlot> ongoing = List.of();
        if (RESULT_FAIL.equals(dto.getResult())) {
            ongoing = timeSlotRepository.findByDeviceId(device.getId()).stream()
                    .filter(s -> PeakCapacityService.STATUS_ACTIVE.equals(s.getStatus()))
                    .filter(s -> !s.getStartDate().isAfter(today) && !s.getEndDate().isBefore(today))
                    .sorted(Comparator.comparing(TimeSlot::getStartDate).thenComparing(TimeSlot::getStartTime))
                    .collect(Collectors.toList());
        }

        saveChangeLog(device, previous.orElse(null), saved, ongoing);
        return new OpeningInspectionSaveResult(saved, ongoing);
    }

    /**
     * 加绑/改绑「生效中」时段的巡检闸口：当天最近一次开班巡检必须是「通过」。
     * 调用方（TimeSlotService）已持有设备行悲观锁，与巡检登记互斥。
     */
    public void assertPassedToday(Device device) {
        Optional<OpeningInspection> latestOpt = latestToday(device.getId());
        if (latestOpt.isEmpty()) {
            throw new InspectionNotPassedException(String.format(
                    "设备【%s】今天还没有开班巡检记录：早班保洁完成开班巡检并登记「通过」前，不能给它新挂「生效中」时段",
                    device.getDeviceCode()));
        }
        OpeningInspection latest = latestOpt.get();
        if (!RESULT_PASS.equals(latest.getResult())) {
            throw new InspectionNotPassedException(String.format(
                    "设备【%s】今天最近的开班巡检为「不通过」（%s），不能新挂「生效中」时段；"
                            + "名下进行中的占用继续有效，待补检「通过」后再挂",
                    device.getDeviceCode(), formatShortage(latest)));
        }
    }

    private void saveChangeLog(Device device, OpeningInspection previous, OpeningInspection current, List<TimeSlot> ongoing) {
        ChangeLog log = new ChangeLog();
        log.setDeviceId(device.getId());
        log.setDeviceCode(device.getDeviceCode());
        log.setTimeSlotId(null);
        log.setChangeType("开班巡检");
        log.setBeforeValue(previous == null
                ? "今日尚无巡检记录"
                : previous.getResult() + "（" + formatShortage(previous) + "）");
        String after = current.getResult() + "（" + formatShortage(current) + "）";
        if (!ongoing.isEmpty()) {
            String detail = ongoing.stream().map(this::formatTimeSlot).collect(Collectors.joining("；"));
            after += "；进行中占用 " + ongoing.size() + " 段继续有效、不置失效：" + detail;
        }
        log.setAfterValue(after);
        // 巡检人即操作人，台账与变更记录能对上是谁检的
        log.setOperator(current.getInspector());
        changeLogRepository.save(log);
    }

    private String formatShortage(OpeningInspection inspection) {
        List<String> parts = new ArrayList<>();
        if (Boolean.TRUE.equals(inspection.getTowelShortage())) {
            parts.add("缺巾");
        }
        if (Boolean.TRUE.equals(inspection.getPaperShortage())) {
            parts.add("缺纸");
        }
        return parts.isEmpty() ? "无缺巾缺纸" : String.join("、", parts);
    }

    private String formatTimeSlot(TimeSlot timeSlot) {
        return String.format("%s %s-%s",
                timeSlot.getStartDate().format(DATE_FORMATTER) + " ~ " + timeSlot.getEndDate().format(DATE_FORMATTER),
                timeSlot.getStartTime().format(TIME_FORMATTER),
                timeSlot.getEndTime().format(TIME_FORMATTER));
    }
}
