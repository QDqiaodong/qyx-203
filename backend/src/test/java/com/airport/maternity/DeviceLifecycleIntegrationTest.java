package com.airport.maternity;

import com.airport.maternity.dto.DeviceDTO;
import com.airport.maternity.dto.DeviceDeleteResult;
import com.airport.maternity.dto.DeviceSaveResult;
import com.airport.maternity.dto.TimeSlotDTO;
import com.airport.maternity.entity.ChangeLog;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.TimeSlot;
import com.airport.maternity.exception.DeviceInUseException;
import com.airport.maternity.repository.ChangeLogRepository;
import com.airport.maternity.repository.DeviceRepository;
import com.airport.maternity.repository.TimeSlotRepository;
import com.airport.maternity.service.DeviceService;
import com.airport.maternity.service.PeakCapacityService;
import com.airport.maternity.service.TimeSlotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 设备停用/删除与名下时段占用一致性的端到端验证：
 * 1) 停用：未结束占用置失效且留痕，历史不动，统计/高峰口径不再算在用；
 * 2) 删除：有进行中占用先拦截并点名；强制删除先置失效留痕，再同事务清掉设备与全部时段，不留孤儿；
 * 3) 删除与时段编辑并发：不允许设备没了时段还写成生效中；
 * 4) 停用设备不能再挂生效中时段。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeviceLifecycleIntegrationTest {

    @Autowired
    private TimeSlotService timeSlotService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private ChangeLogRepository changeLogRepository;

    @Autowired
    private MockMvc mockMvc;

    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;

    private Device newDevice(String code, String type, String area) {
        DeviceDTO dto = new DeviceDTO();
        dto.setDeviceCode(code);
        dto.setDeviceType(type);
        dto.setTerminalArea(area);
        dto.setStatus("正常");
        return deviceService.save(dto).getDevice();
    }

    private TimeSlot newSlot(Long deviceId, LocalDate startDate, LocalDate endDate, String start, String end) {
        TimeSlotDTO dto = new TimeSlotDTO();
        dto.setDeviceId(deviceId);
        dto.setStartDate(startDate.format(DF));
        dto.setEndDate(endDate.format(DF));
        dto.setStartTime(start);
        dto.setEndTime(end);
        dto.setStatus("生效中");
        return timeSlotService.save(dto);
    }

    private DeviceDTO editDto(Device d, String status) {
        DeviceDTO dto = new DeviceDTO();
        dto.setId(d.getId());
        dto.setDeviceCode(d.getDeviceCode());
        dto.setDeviceType(d.getDeviceType());
        dto.setTerminalArea(d.getTerminalArea());
        dto.setStatus(status);
        return dto;
    }

    private List<ChangeLog> logs(Long deviceId) {
        return changeLogRepository.findByDeviceId(deviceId, PageRequest.of(0, 100)).getContent();
    }

    @Test
    void 停用后未结束占用全部失效并留痕_历史不动() {
        LocalDate today = LocalDate.now();
        Device d = newDevice("LIFE-001", "哺乳室", "T1航站楼");
        TimeSlot ongoing = newSlot(d.getId(), today.minusDays(1), today.plusDays(2), "09:30", "10:30");
        TimeSlot future = newSlot(d.getId(), today.plusDays(3), today.plusDays(5), "10:30", "11:30");
        TimeSlot history = newSlot(d.getId(), today.minusDays(6), today.minusDays(3), "13:00", "14:00");

        DeviceSaveResult result = deviceService.save(editDto(d, "停用"));

        // 返回结果点名了被带走的 2 段未结束占用
        assertEquals(2, result.getInvalidatedSlots().size());
        assertEquals(DeviceService.REASON_DISABLED, result.getReason());

        Map<Long, String> statusById = timeSlotRepository.findByDeviceId(d.getId()).stream()
                .collect(Collectors.toMap(TimeSlot::getId, TimeSlot::getStatus));
        assertEquals("已失效", statusById.get(ongoing.getId()));
        assertEquals("已失效", statusById.get(future.getId()));
        // 已结束的历史时段不假装失效
        assertEquals("生效中", statusById.get(history.getId()));

        // 变更记录对得上：一条设备停用 + 两段时段失效，且带设备编号快照
        List<ChangeLog> logs = logs(d.getId());
        assertTrue(logs.stream().anyMatch(l -> "设备停用".equals(l.getChangeType())));
        long invalidatedLogs = logs.stream()
                .filter(l -> "时段失效".equals(l.getChangeType()))
                .peek(l -> assertEquals("LIFE-001", l.getDeviceCode()))
                .count();
        assertEquals(2, invalidatedLogs);
        assertTrue(logs.stream().anyMatch(l -> "时段失效".equals(l.getChangeType())
                && ongoing.getId().equals(l.getTimeSlotId())
                && l.getBeforeValue().contains("进行中")));
        assertTrue(logs.stream().anyMatch(l -> "时段失效".equals(l.getChangeType())
                && future.getId().equals(l.getTimeSlotId())
                && l.getBeforeValue().contains("未开始")));

        // 统计口径：停用设备不再出现在按时段筛选结果中
        List<Long> inUse = timeSlotService.findInUseDeviceIdsByTimeRange("09:30", "10:30");
        assertFalse(inUse.contains(d.getId()));

        // 已结束的历史时段也不会把停用设备捞回在用名单
        List<Long> byHistory = timeSlotService.findInUseDeviceIdsByTimeRange("13:00", "14:00");
        assertFalse(byHistory.contains(d.getId()));
    }

    @Test
    void 停用设备不能再保存生效中时段() {
        LocalDate today = LocalDate.now();
        Device d = newDevice("LIFE-002", "母婴室", "T2航站楼");
        deviceService.save(editDto(d, "停用"));
        assertThrows(IllegalArgumentException.class, () ->
                newSlot(d.getId(), today.plusDays(1), today.plusDays(2), "09:30", "10:30"));
        // 被拦截后不能落库
        assertTrue(timeSlotRepository.findByDeviceId(d.getId()).stream()
                .noneMatch(s -> today.plusDays(1).equals(s.getStartDate())));
    }

    @Test
    void 有进行中占用时删除被拦截并点名时段_不删任何数据() {
        LocalDate today = LocalDate.now();
        Device d = newDevice("LIFE-003", "哺乳室", "T2航站楼");
        TimeSlot ongoing = newSlot(d.getId(), today.minusDays(1), today.plusDays(1), "09:30", "10:30");
        TimeSlot future = newSlot(d.getId(), today.plusDays(2), today.plusDays(3), "10:30", "11:30");

        DeviceInUseException ex = assertThrows(DeviceInUseException.class,
                () -> deviceService.deleteById(d.getId(), false));
        // 点名进行中的具体时段，且不把未开始时段混进拦截名单
        assertTrue(ex.getMessage().contains("LIFE-003"));
        assertTrue(ex.getMessage().contains(today.minusDays(1).format(DF)));
        assertFalse(ex.getMessage().contains(today.plusDays(2).format(DF)));

        // 被拦截后设备和时段都还在，状态不变
        assertTrue(deviceRepository.findById(d.getId()).isPresent());
        assertEquals(2, timeSlotRepository.findByDeviceId(d.getId()).size());
        assertEquals(PeakCapacityService.STATUS_ACTIVE,
                timeSlotRepository.findById(ongoing.getId()).orElseThrow().getStatus());
        assertEquals(PeakCapacityService.STATUS_ACTIVE,
                timeSlotRepository.findById(future.getId()).orElseThrow().getStatus());
    }

    @Test
    void 强制删除先把未结束占用置失效留痕_再同事务清空不留孤儿() {
        LocalDate today = LocalDate.now();
        Device d = newDevice("LIFE-004", "育婴室", "T3航站楼");
        TimeSlot ongoing = newSlot(d.getId(), today.minusDays(1), today.plusDays(1), "09:30", "10:30");
        TimeSlot future = newSlot(d.getId(), today.plusDays(2), today.plusDays(3), "10:30", "11:30");
        TimeSlot history = newSlot(d.getId(), today.minusDays(5), today.minusDays(3), "13:00", "14:00");

        DeviceDeleteResult result = deviceService.deleteById(d.getId(), true);
        // 共带走 3 段，其中 2 段未结束先置失效，且记录了进行中数量
        assertEquals(3, result.getTotalSlots());
        assertEquals(2, result.getInvalidatedSlots());
        assertEquals(1, result.getOngoingSlots());
        assertTrue(result.isForced());

        // 设备行没了，名下一个时段都不能剩（无孤儿时段）
        assertTrue(deviceRepository.findById(d.getId()).isEmpty());
        assertEquals(0, timeSlotRepository.findByDeviceId(d.getId()).size());

        // 变更记录仍在，且能对上台账编号、对上带走了哪几段
        List<ChangeLog> logs = logs(d.getId());
        assertTrue(logs.stream().anyMatch(l -> "设备删除".equals(l.getChangeType())
                && "LIFE-004".equals(l.getDeviceCode())));
        assertTrue(logs.stream().anyMatch(l -> "时段失效".equals(l.getChangeType())
                && ongoing.getId().equals(l.getTimeSlotId())
                && l.getBeforeValue().contains("进行中")));
        assertTrue(logs.stream().anyMatch(l -> "时段失效".equals(l.getChangeType())
                && future.getId().equals(l.getTimeSlotId())
                && l.getBeforeValue().contains("未开始")));
        // 历史时段不写失效记录
        assertTrue(logs.stream().noneMatch(l -> "时段失效".equals(l.getChangeType())
                && history.getId().equals(l.getTimeSlotId())));
    }

    @Test
    void 没有进行中占用时普通删除同样清空全部时段并留汇总记录() {
        LocalDate today = LocalDate.now();
        Device d = newDevice("LIFE-005", "母婴室", "T1航站楼");
        // 只有未开始时段：不拦截
        newSlot(d.getId(), today.plusDays(1), today.plusDays(2), "09:30", "10:30");
        DeviceDeleteResult result = deviceService.deleteById(d.getId(), false);
        assertEquals(1, result.getTotalSlots());
        assertEquals(1, result.getInvalidatedSlots());
        assertEquals(0, result.getOngoingSlots());
        assertTrue(deviceRepository.findById(d.getId()).isEmpty());
        assertEquals(0, timeSlotRepository.findByDeviceId(d.getId()).size());
        assertTrue(logs(d.getId()).stream().anyMatch(l -> "设备删除".equals(l.getChangeType())));
    }

    @Test
    void 删除与时段并发编辑_不会出现设备没了时段还生效中() throws Exception {
        LocalDate today = LocalDate.now();
        Device d = newDevice("LIFE-006", "哺乳室", "T1航站楼");
        TimeSlot slot = newSlot(d.getId(), today.minusDays(1), today.plusDays(2), "09:30", "10:30");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        // 线程A：强制删除设备（先失效再清空）
        java.util.concurrent.Callable<String> deleteTask = () -> {
            ready.countDown();
            go.await();
            try {
                deviceService.deleteById(d.getId(), true);
                return "DELETED";
            } catch (Exception e) {
                return "ERROR:" + e.getClass().getSimpleName();
            }
        };
        // 线程B：并发编辑同一设备名下时段为生效中
        java.util.concurrent.Callable<String> editTask = () -> {
            ready.countDown();
            go.await();
            try {
                TimeSlotDTO dto = new TimeSlotDTO();
                dto.setId(slot.getId());
                dto.setDeviceId(d.getId());
                dto.setStartDate(today.minusDays(1).format(DF));
                dto.setEndDate(today.plusDays(2).format(DF));
                dto.setStartTime("09:30");
                dto.setEndTime("11:00");
                dto.setStatus("生效中");
                timeSlotService.save(dto);
                return "SAVED";
            } catch (Exception e) {
                return "ERROR:" + e.getClass().getSimpleName();
            }
        };

        Future<String> fDelete = pool.submit(deleteTask);
        Future<String> fEdit = pool.submit(editTask);
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        String rDelete = fDelete.get(30, TimeUnit.SECONDS);
        String rEdit = fEdit.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // 终态只能二选一，且不能留下「设备没了、时段还生效中」的组合
        boolean deviceExists = deviceRepository.findById(d.getId()).isPresent();
        List<TimeSlot> left = timeSlotRepository.findByDeviceId(d.getId());
        if ("DELETED".equals(rDelete)) {
            assertFalse(deviceExists);
            assertEquals(0, left.size(), "设备删除后不得留下孤儿时段");
        } else {
            // 删除失败时编辑可以成功，但设备必然还在
            assertTrue(deviceExists);
            assertTrue(left.stream().allMatch(s -> s.getDeviceId().equals(d.getId())));
        }
    }

    @Test
    void 接口层删除遇进行中占用返回409并点名_强制删除走通() throws Exception {
        LocalDate today = LocalDate.now();
        Device d = newDevice("LIFE-007", "母婴室", "T1航站楼");
        newSlot(d.getId(), today.minusDays(1), today.plusDays(1), "09:30", "10:30");

        // 默认删除被拦：409 且点名设备编号
        mockMvc.perform(delete("/api/devices/" + d.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("LIFE-007")));
        assertTrue(deviceRepository.findById(d.getId()).isPresent());

        // 强制删除走通：设备与时段一起清掉
        mockMvc.perform(delete("/api/devices/" + d.getId()).param("force", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalSlots").value(1))
                .andExpect(jsonPath("$.data.invalidatedSlots").value(1));
        assertTrue(deviceRepository.findById(d.getId()).isEmpty());
        assertEquals(0, timeSlotRepository.findByDeviceId(d.getId()).size());
    }

    @Test
    void 时段换绑到另一台设备的加锁路径不影响正常操作() {
        LocalDate today = LocalDate.now();
        Device a = newDevice("LIFE-008", "育婴室", "T2航站楼");
        Device b = newDevice("LIFE-009", "育婴室", "T3航站楼");
        TimeSlot slot = newSlot(a.getId(), today.plusDays(1), today.plusDays(2), "09:30", "10:30");

        TimeSlotDTO move = new TimeSlotDTO();
        move.setId(slot.getId());
        move.setDeviceId(b.getId());
        move.setStartDate(today.plusDays(1).format(DF));
        move.setEndDate(today.plusDays(2).format(DF));
        move.setStartTime("09:30");
        move.setEndTime("10:30");
        move.setStatus("生效中");
        TimeSlot moved = timeSlotService.save(move);

        assertEquals(b.getId(), moved.getDeviceId());
        assertEquals(0, timeSlotRepository.findByDeviceId(a.getId()).size());
        assertEquals(1, timeSlotRepository.findByDeviceId(b.getId()).size());
    }
}
