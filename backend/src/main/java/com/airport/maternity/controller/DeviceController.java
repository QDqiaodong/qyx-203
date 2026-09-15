package com.airport.maternity.controller;

import com.airport.maternity.dto.DeviceDTO;
import com.airport.maternity.dto.DeviceDeleteResult;
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
            String prefix;
            if (DeviceService.REASON_DISABLED.equals(result.getReason())) {
                // 停用带走的占用含进行中的时段，提示口径与停用语义一致
                prefix = "保存成功。设备已停用，名下 " + invalidated.size()
                        + " 段尚未结束的占用已置为失效，不再计入时段列表与统计：";
            } else {
                prefix = "保存成功。新分区高峰名额不足，以下 " + invalidated.size()
                        + " 段尚未开始的时段已置为失效：";
            }
            return ResponseDTO.success(prefix + detail, result.getDevice());
        }
        return ResponseDTO.success(result.getDevice());
    }

    /**
     * 删除设备：默认遇到进行中占用返回 409 并点名具体时段；
     * force=true 时强制删除——未结束占用先置失效并写变更，设备与其全部时段同事务清除。
     */
    @DeleteMapping("/{id}")
    public ResponseDTO<DeviceDeleteResult> delete(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean force) {
        DeviceDeleteResult result = deviceService.deleteById(id, force);
        String message = String.format(
                "设备已删除：名下 %d 段时段已一并清除，无孤儿时段；其中 %d 段未结束占用已先置为失效并写入变更记录。",
                result.getTotalSlots(), result.getInvalidatedSlots());
        return ResponseDTO.success(message, result);
    }
}
