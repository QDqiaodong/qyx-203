package com.airport.maternity.service;

import com.airport.maternity.dto.PeakCapacityLimitDTO;
import com.airport.maternity.dto.PeakCapacityStatusDTO;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.PeakCapacityLimit;
import com.airport.maternity.entity.PeakWindow;
import com.airport.maternity.entity.TimeSlot;
import com.airport.maternity.exception.CapacityExceededException;
import com.airport.maternity.repository.PeakCapacityLimitRepository;
import com.airport.maternity.repository.PeakWindowRepository;
import com.airport.maternity.repository.TimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 高峰窗同时在用上限：
 * 按「航站楼分区 + 设备类型」配置上限；时段加绑/调整、设备调区时，
 * 只要生效中时段在高峰窗内叠加后会把同时在用数顶过上限，即拦截或置失效。
 */
@Service
public class PeakCapacityService {

    public static final String STATUS_ACTIVE = "生效中";
    public static final String STATUS_INVALIDATED = "已失效";

    @Autowired
    private PeakCapacityLimitRepository limitRepository;

    @Autowired
    private PeakWindowRepository peakWindowRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    public List<PeakWindow> getPeakWindows() {
        return peakWindowRepository.findAllByOrderByStartTimeAsc();
    }

    public List<PeakCapacityStatusDTO> listLimitsWithUsage() {
        return limitRepository.findAllByOrderByTerminalAreaAscDeviceTypeAsc().stream()
                .map(l -> new PeakCapacityStatusDTO(
                        l.getId(),
                        l.getTerminalArea(),
                        l.getDeviceType(),
                        l.getMaxConcurrent(),
                        currentPeakUsage(l.getTerminalArea(), l.getDeviceType()),
                        l.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public PeakCapacityLimit saveLimit(PeakCapacityLimitDTO dto) {
        PeakCapacityLimit limit;
        if (dto.getId() != null) {
            limit = limitRepository.findById(dto.getId()).orElse(new PeakCapacityLimit());
            // 改成已被其它记录占用的「分区+类型」时拒绝，避免唯一约束冲突
            Optional<PeakCapacityLimit> duplicated = limitRepository
                    .findByTerminalAreaAndDeviceType(dto.getTerminalArea(), dto.getDeviceType());
            if (duplicated.isPresent() && !duplicated.get().getId().equals(limit.getId())) {
                throw new IllegalArgumentException(
                        dto.getTerminalArea() + " · " + dto.getDeviceType() + " 已配置高峰上限，请直接编辑该条配置");
            }
        } else {
            // 同一「分区+类型」只保留一条，重复配置视为调整上限
            limit = limitRepository
                    .findByTerminalAreaAndDeviceType(dto.getTerminalArea(), dto.getDeviceType())
                    .orElse(new PeakCapacityLimit());
        }
        limit.setTerminalArea(dto.getTerminalArea());
        limit.setDeviceType(dto.getDeviceType());
        limit.setMaxConcurrent(dto.getMaxConcurrent());
        return limitRepository.save(limit);
    }

    @Transactional
    public void deleteLimit(Long id) {
        limitRepository.deleteById(id);
    }

    /**
     * 今日高峰窗内该「分区+类型」的同时在用峰值（生效中且今日在日期范围内的时段，
     * 截断到各高峰窗后做扫描线求最大并发）。
     */
    public int currentPeakUsage(String terminalArea, String deviceType) {
        LocalDate today = LocalDate.now();
        List<TimeSlot> slots = timeSlotRepository
                .findByAreaAndTypeAndStatus(terminalArea, deviceType, STATUS_ACTIVE)
                .stream()
                .filter(s -> !s.getStartDate().isAfter(today) && !s.getEndDate().isBefore(today))
                .collect(Collectors.toList());
        int max = 0;
        for (PeakWindow window : getPeakWindows()) {
            max = Math.max(max, maxConcurrentInWindow(slots, window));
        }
        return max;
    }

    /**
     * 加绑/改时段校验：候选时段若在任一高峰窗内与该分区该类型已有「生效中」时段叠加，
     * 且叠加后同时在用数超过上限，则抛出 CapacityExceededException，本次保存不落下。
     * 通过悲观锁锁住上限配置行，并发加绑时只有一方能占到最后的名额。
     *
     * @param device        候选时段所属设备（决定分区与类型）
     * @param candidate     候选时段（日期范围 + 每日时段）
     * @param excludeSlotId 编辑场景下需排除的自身时段ID，新增传 null
     */
    public void assertWithinCapacity(Device device, TimeSlot candidate, Long excludeSlotId) {
        Optional<PeakCapacityLimit> limitOpt = limitRepository
                .findByAreaAndTypeForUpdate(device.getTerminalArea(), device.getDeviceType());
        if (limitOpt.isEmpty()) {
            return; // 该分区该类型未配置上限，不限制
        }
        PeakCapacityLimit limit = limitOpt.get();
        List<TimeSlot> activeSlots = timeSlotRepository.findByAreaAndTypeAndStatus(
                device.getTerminalArea(), device.getDeviceType(), STATUS_ACTIVE);

        for (PeakWindow window : getPeakWindows()) {
            if (trimToWindow(candidate, window) == null) {
                continue; // 候选时段不进该高峰窗
            }
            long overlapping = activeSlots.stream()
                    .filter(s -> excludeSlotId == null || !s.getId().equals(excludeSlotId))
                    .filter(s -> dateRangesOverlap(s, candidate))
                    .filter(s -> overlapsInWindow(s, candidate, window))
                    .count();
            if (overlapping + 1 > limit.getMaxConcurrent()) {
                throw new CapacityExceededException(String.format(
                        "【%s · %s】%s（%s-%s）内已有 %d 台同时在用，达到上限 %d 台，本次加绑/调整未保存（高峰名额可能刚被其它操作占用，请刷新确认）",
                        limit.getTerminalArea(), limit.getDeviceType(),
                        window.getName(),
                        window.getStartTime(), window.getEndTime(),
                        overlapping, limit.getMaxConcurrent()));
            }
        }
    }

    /**
     * 设备调区（或改类型）后的存量时段 reconciling：
     * - 已经结束的历史时段：不动；
     * - 已经开始（进行中）的时段：保持生效，且不占新分区高峰名额；
     * - 尚未开始的时段：逐个与新分区高峰上限核对，会超员的置为「已失效」。
     *
     * @return 被置为失效的时段列表
     */
    public List<TimeSlot> reconcileDeviceMove(Device device, String newArea, String newType) {
        Optional<PeakCapacityLimit> limitOpt = limitRepository.findByAreaAndTypeForUpdate(newArea, newType);
        if (limitOpt.isEmpty()) {
            return List.of(); // 新分区该类型未配置上限，不限制
        }
        PeakCapacityLimit limit = limitOpt.get();
        LocalDate today = LocalDate.now();

        List<TimeSlot> deviceSlots = timeSlotRepository.findByDeviceId(device.getId());
        // 尚未开始的生效中时段，按开始先后逐个核对
        List<TimeSlot> futureSlots = deviceSlots.stream()
                .filter(s -> STATUS_ACTIVE.equals(s.getStatus()))
                .filter(s -> s.getStartDate().isAfter(today))
                .sorted(Comparator.comparing(TimeSlot::getStartDate).thenComparing(TimeSlot::getStartTime))
                .collect(Collectors.toList());
        if (futureSlots.isEmpty()) {
            return List.of();
        }

        // 新分区里其它设备的生效中时段（本台设备已经开始的占用不用新分区名额去补，故整体排除本台）
        List<TimeSlot> othersActive = timeSlotRepository
                .findByAreaAndTypeAndStatus(newArea, newType, STATUS_ACTIVE)
                .stream()
                .filter(s -> !s.getDeviceId().equals(device.getId()))
                .collect(Collectors.toList());

        List<TimeSlot> accepted = new ArrayList<>();
        List<TimeSlot> invalidated = new ArrayList<>();
        for (TimeSlot slot : futureSlots) {
            long inUse = countOverlappingInAnyWindow(othersActive, slot)
                    + countOverlappingInAnyWindow(accepted, slot);
            if (inUse + 1 > limit.getMaxConcurrent()) {
                slot.setStatus(STATUS_INVALIDATED);
                timeSlotRepository.save(slot);
                invalidated.add(slot);
            } else {
                accepted.add(slot);
            }
        }
        return invalidated;
    }

    /** 在任一高峰窗内与 candidate 日期相交且时段叠加的条数 */
    private long countOverlappingInAnyWindow(List<TimeSlot> slots, TimeSlot candidate) {
        return slots.stream()
                .filter(s -> dateRangesOverlap(s, candidate))
                .filter(s -> getPeakWindows().stream().anyMatch(w -> overlapsInWindow(s, candidate, w)))
                .count();
    }

    /** 两段日期范围是否相交 */
    private boolean dateRangesOverlap(TimeSlot a, TimeSlot b) {
        return !a.getStartDate().isAfter(b.getEndDate()) && !b.getStartDate().isAfter(a.getEndDate());
    }

    /** 两段时段截断到同一高峰窗后是否仍有交叠 */
    private boolean overlapsInWindow(TimeSlot a, TimeSlot b, PeakWindow window) {
        LocalTime[] ta = trimToWindow(a, window);
        LocalTime[] tb = trimToWindow(b, window);
        if (ta == null || tb == null) {
            return false;
        }
        return ta[0].isBefore(tb[1]) && tb[0].isBefore(ta[1]);
    }

    /** 把时段的每日时间范围截断到高峰窗内，不相交返回 null */
    private LocalTime[] trimToWindow(TimeSlot slot, PeakWindow window) {
        LocalTime start = slot.getStartTime().isAfter(window.getStartTime()) ? slot.getStartTime() : window.getStartTime();
        LocalTime end = slot.getEndTime().isBefore(window.getEndTime()) ? slot.getEndTime() : window.getEndTime();
        if (!start.isBefore(end)) {
            return null;
        }
        return new LocalTime[]{start, end};
    }

    /** 扫描线求一组时段在某一高峰窗内的最大并发数 */
    private int maxConcurrentInWindow(List<TimeSlot> slots, PeakWindow window) {
        List<int[]> events = new ArrayList<>(); // [秒, 增量]
        for (TimeSlot slot : slots) {
            LocalTime[] trimmed = trimToWindow(slot, window);
            if (trimmed != null) {
                events.add(new int[]{trimmed[0].toSecondOfDay(), 1});
                events.add(new int[]{trimmed[1].toSecondOfDay(), -1});
            }
        }
        // 同一时刻先处理结束(-1)再处理开始(+1)，首尾相接不算同时
        events.sort(Comparator.comparingInt((int[] e) -> e[0]).thenComparingInt(e -> e[1]));
        int current = 0;
        int max = 0;
        for (int[] event : events) {
            current += event[1];
            max = Math.max(max, current);
        }
        return max;
    }
}
