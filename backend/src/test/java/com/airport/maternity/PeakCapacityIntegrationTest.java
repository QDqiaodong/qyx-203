package com.airport.maternity;

import com.airport.maternity.dto.DeviceDTO;
import com.airport.maternity.dto.DeviceSaveResult;
import com.airport.maternity.dto.OpeningInspectionDTO;
import com.airport.maternity.dto.PeakCapacityLimitDTO;
import com.airport.maternity.dto.PeakCapacityStatusDTO;
import com.airport.maternity.dto.TimeSlotDTO;
import com.airport.maternity.entity.ChangeLog;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.TimeSlot;
import com.airport.maternity.exception.CapacityExceededException;
import com.airport.maternity.repository.ChangeLogRepository;
import com.airport.maternity.repository.TimeSlotRepository;
import com.airport.maternity.service.DeviceService;
import com.airport.maternity.service.OpeningInspectionService;
import com.airport.maternity.service.PeakCapacityService;
import com.airport.maternity.service.TimeSlotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 高峰同时在用上限的端到端验证：
 * 1) 按分区+类型配置上限；2) 加绑/改时段超上限即拦截不落库；
 * 3) 调区后存量时段 reconciling；4) 并发加绑只有一个占到最后名额；
 * 5) 上限、同时在用数、时段状态均可从持久层一致读出。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PeakCapacityIntegrationTest {

    @Autowired
    private TimeSlotService timeSlotService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private PeakCapacityService peakCapacityService;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private ChangeLogRepository changeLogRepository;

    @Autowired
    private OpeningInspectionService openingInspectionService;

    @Autowired
    private MockMvc mockMvc;

    private static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;

    private Device newDevice(String code, String type, String area) {
        DeviceDTO dto = new DeviceDTO();
        dto.setDeviceCode(code);
        dto.setDeviceType(type);
        dto.setTerminalArea(area);
        dto.setStatus("正常");
        Device device = deviceService.save(dto).getDevice();
        // 新规：当天最近一次开班巡检「通过」后才允许挂「生效中」时段
        passInspection(device.getId());
        return device;
    }

    private void passInspection(Long deviceId) {
        OpeningInspectionDTO dto = new OpeningInspectionDTO();
        dto.setDeviceId(deviceId);
        dto.setInspector("早班保洁");
        dto.setResult("通过");
        dto.setTowelShortage(false);
        dto.setPaperShortage(false);
        openingInspectionService.save(dto);
    }

    private TimeSlotDTO slotDto(Long deviceId, LocalDate startDate, LocalDate endDate,
                                String startTime, String endTime) {
        TimeSlotDTO dto = new TimeSlotDTO();
        dto.setDeviceId(deviceId);
        dto.setStartDate(startDate.format(DF));
        dto.setEndDate(endDate.format(DF));
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        dto.setStatus("生效中");
        return dto;
    }

    private void newLimit(String area, String type, int max) {
        PeakCapacityLimitDTO dto = new PeakCapacityLimitDTO();
        dto.setTerminalArea(area);
        dto.setDeviceType(type);
        dto.setMaxConcurrent(max);
        peakCapacityService.saveLimit(dto);
    }

    @Test
    void 高峰窗已初始化两段() {
        List<String> names = peakCapacityService.getPeakWindows().stream()
                .map(w -> w.getName()).collect(Collectors.toList());
        assertEquals(2, names.size());
        assertTrue(names.contains("早出港高峰"));
        assertTrue(names.contains("晚进港高峰"));
    }

    @Test
    void 加绑超上限被拦截且不落库() {
        LocalDate today = LocalDate.now();
        newLimit("T2航站楼", "哺乳室", 1);
        Device d1 = newDevice("T2-WS-001", "哺乳室", "T2航站楼");
        Device d2 = newDevice("T2-WS-002", "哺乳室", "T2航站楼");

        // 第一段进早高峰：可保存
        TimeSlot s1 = timeSlotService.save(slotDto(d1.getId(), today, today.plusDays(5), "07:00", "08:00"));
        assertNotNull(s1.getId());

        // 第二段与它在早高峰叠加：顶过上限，必须拦截
        CapacityExceededException ex = assertThrows(CapacityExceededException.class, () ->
                timeSlotService.save(slotDto(d2.getId(), today, today.plusDays(5), "07:30", "08:30")));
        assertTrue(ex.getMessage().contains("T2航站楼"));
        assertTrue(ex.getMessage().contains("哺乳室"));
        assertTrue(ex.getMessage().contains("上限"));

        // 被拦截的时段不能落库
        List<TimeSlot> d2Slots = timeSlotRepository.findByDeviceId(d2.getId());
        assertEquals(0, d2Slots.size());

        // 不进高峰窗的时段不受限：可保存
        TimeSlot s3 = timeSlotService.save(slotDto(d2.getId(), today, today.plusDays(5), "10:00", "11:00"));
        assertNotNull(s3.getId());

        // 晚进港高峰与早高峰分别计算：早高峰满员不影响晚高峰加绑
        TimeSlot s4 = timeSlotService.save(slotDto(d2.getId(), today, today.plusDays(5), "21:30", "22:30"));
        assertNotNull(s4.getId());

        // 同时在用数按分区+类型实时重算
        assertEquals(1, peakCapacityService.currentPeakUsage("T2航站楼", "哺乳室"));
    }

    @Test
    void 改时段同样受上限约束_停用后名额释放() {
        LocalDate today = LocalDate.now();
        newLimit("T1航站楼", "婴儿护理台", 1);
        Device e1 = newDevice("T1-HL-001", "婴儿护理台", "T1航站楼");
        Device e2 = newDevice("T1-HL-002", "婴儿护理台", "T1航站楼");

        TimeSlot slotA = timeSlotService.save(slotDto(e1.getId(), today, today.plusDays(2), "07:00", "08:00"));
        TimeSlot slotB = timeSlotService.save(slotDto(e2.getId(), today, today.plusDays(2), "10:00", "11:00"));

        // 把 slotB 改进早高峰并与 slotA 叠加：拦截
        TimeSlotDTO move = slotDto(e2.getId(), today, today.plusDays(2), "07:30", "08:30");
        move.setId(slotB.getId());
        assertThrows(CapacityExceededException.class, () -> timeSlotService.save(move));

        // 改自身但不挤占别人（排除自身）：允许
        TimeSlotDTO extend = slotDto(e1.getId(), today, today.plusDays(4), "07:00", "08:00");
        extend.setId(slotA.getId());
        assertDoesNotThrow(() -> timeSlotService.save(extend));

        // slotA 停用后名额释放，slotB 可迁入早高峰
        TimeSlotDTO disable = slotDto(e1.getId(), today, today.plusDays(4), "07:00", "08:00");
        disable.setId(slotA.getId());
        disable.setStatus("已停用");
        timeSlotService.save(disable);
        assertDoesNotThrow(() -> timeSlotService.save(move));
        assertEquals(1, peakCapacityService.currentPeakUsage("T1航站楼", "婴儿护理台"));
    }

    @Test
    void 调区后未来超员时段置失效_进行中保持生效且不占新分区名额_历史不动() {
        LocalDate today = LocalDate.now();
        newLimit("T3航站楼", "育婴室", 2);

        // 新分区已有 1 台在高峰占用
        Device b = newDevice("T3-YY-100", "育婴室", "T3航站楼");
        timeSlotService.save(slotDto(b.getId(), today.plusDays(2), today.plusDays(3), "07:00", "08:00"));

        // 待调区设备：进行中1段、未来2段、已结束1段
        Device a = newDevice("T1-YY-001", "育婴室", "T1航站楼");
        TimeSlot started = timeSlotService.save(slotDto(a.getId(), today.minusDays(1), today.plusDays(3), "07:00", "08:00"));
        TimeSlot future1 = timeSlotService.save(slotDto(a.getId(), today.plusDays(2), today.plusDays(3), "07:00", "08:00"));
        TimeSlot future2 = timeSlotService.save(slotDto(a.getId(), today.plusDays(2), today.plusDays(3), "07:30", "08:30"));
        TimeSlot history = timeSlotService.save(slotDto(a.getId(), today.minusDays(5), today.minusDays(3), "07:00", "08:00"));

        // 调区 T1 -> T3
        DeviceDTO moveDto = new DeviceDTO();
        moveDto.setId(a.getId());
        moveDto.setDeviceCode(a.getDeviceCode());
        moveDto.setDeviceType("育婴室");
        moveDto.setTerminalArea("T3航站楼");
        moveDto.setStatus("正常");
        DeviceSaveResult result = deviceService.save(moveDto);

        // 点名出会超员的时段：只有 future2 超员（B1=1 + future1=1 = 2 已达上限）
        assertEquals(1, result.getInvalidatedSlots().size());
        assertEquals(future2.getId(), result.getInvalidatedSlots().get(0).getId());

        Map<Long, String> statusById = timeSlotRepository.findByDeviceId(a.getId()).stream()
                .collect(Collectors.toMap(TimeSlot::getId, TimeSlot::getStatus));
        // 已经开始的保持生效，且没有吃掉新分区名额（future1 因此得以保留）
        assertEquals("生效中", statusById.get(started.getId()));
        assertEquals("生效中", statusById.get(future1.getId()));
        // 会超员且尚未开始的置为失效
        assertEquals("已失效", statusById.get(future2.getId()));
        // 已结束的历史时段不动
        assertEquals("生效中", statusById.get(history.getId()));

        // 变更记录留痕：调区 + 时段失效
        List<ChangeLog> logs = changeLogRepository
                .findByDeviceId(a.getId(), PageRequest.of(0, 50)).getContent();
        assertTrue(logs.stream().anyMatch(l -> "设备调区".equals(l.getChangeType())));
        assertTrue(logs.stream().anyMatch(l -> "时段失效".equals(l.getChangeType())
                && future2.getId().equals(l.getTimeSlotId())));
    }

    @Test
    void 并发加绑只有一个能占到最后名额() throws Exception {
        LocalDate today = LocalDate.now();
        newLimit("国际出发区", "婴儿护理台", 2);
        Device g1 = newDevice("GJ-HL-001", "婴儿护理台", "国际出发区");
        Device g2 = newDevice("GJ-HL-002", "婴儿护理台", "国际出发区");
        Device g3 = newDevice("GJ-HL-003", "婴儿护理台", "国际出发区");
        // 已占 1 台，仅剩 1 个名额
        timeSlotService.save(slotDto(g1.getId(), today, today.plusDays(1), "07:00", "08:00"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        java.util.concurrent.Callable<String> taskG2 = () -> {
            ready.countDown();
            go.await();
            try {
                timeSlotService.save(slotDto(g2.getId(), today, today.plusDays(1), "07:00", "08:00"));
                return "OK";
            } catch (CapacityExceededException e) {
                return "REJECTED";
            }
        };
        java.util.concurrent.Callable<String> taskG3 = () -> {
            ready.countDown();
            go.await();
            try {
                timeSlotService.save(slotDto(g3.getId(), today, today.plusDays(1), "07:00", "08:00"));
                return "OK";
            } catch (CapacityExceededException e) {
                return "REJECTED";
            }
        };

        Future<String> f1 = pool.submit(taskG2);
        Future<String> f2 = pool.submit(taskG3);
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        String r1 = f1.get(30, TimeUnit.SECONDS);
        String r2 = f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // 两边看到都像还能再加一台，但只有一台真正加上
        assertEquals(1, List.of(r1, r2).stream().filter("OK"::equals).count());
        assertEquals(1, List.of(r1, r2).stream().filter("REJECTED"::equals).count());

        // 不能两段都变成生效中：该分区该类型生效中时段数 == 上限
        List<TimeSlot> active = timeSlotRepository
                .findByAreaAndTypeAndStatus("国际出发区", "婴儿护理台", "生效中");
        assertEquals(2, active.size());
        assertEquals(2, peakCapacityService.currentPeakUsage("国际出发区", "婴儿护理台"));
    }

    @Test
    void 接口层超上限返回业务错误且不落库() throws Exception {
        LocalDate today = LocalDate.now();
        newLimit("国内到达区", "母婴室", 1);
        Device m1 = newDevice("GN-MY-001", "母婴室", "国内到达区");
        Device m2 = newDevice("GN-MY-002", "母婴室", "国内到达区");

        String slot1 = """
                {"deviceId": %d, "startDate": "%s", "endDate": "%s", "startTime": "07:00", "endTime": "08:00", "status": "生效中"}
                """.formatted(m1.getId(), today.format(DF), today.plusDays(1).format(DF));
        mockMvc.perform(post("/api/time-slots").contentType(MediaType.APPLICATION_JSON).content(slot1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String slot2 = """
                {"deviceId": %d, "startDate": "%s", "endDate": "%s", "startTime": "07:30", "endTime": "08:30", "status": "生效中"}
                """.formatted(m2.getId(), today.format(DF), today.plusDays(1).format(DF));
        mockMvc.perform(post("/api/time-slots").contentType(MediaType.APPLICATION_JSON).content(slot2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("上限")));

        assertEquals(0, timeSlotRepository.findByDeviceId(m2.getId()).size());

        // 高峰窗与上限（含当前同时在用数）接口可一致读出
        mockMvc.perform(get("/api/peak-capacity/windows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/peak-capacity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.terminalArea == '国内到达区' && @.deviceType == '母婴室')].currentUsage")
                        .value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void 上限列表带出各分区同时在用数() {
        LocalDate today = LocalDate.now();
        newLimit("T2航站楼", "母婴室", 3);
        Device p1 = newDevice("T2-MY-001", "母婴室", "T2航站楼");
        Device p2 = newDevice("T2-MY-002", "母婴室", "T2航站楼");
        timeSlotService.save(slotDto(p1.getId(), today, today.plusDays(1), "07:00", "08:00"));
        timeSlotService.save(slotDto(p2.getId(), today, today.plusDays(1), "07:30", "08:30"));

        List<PeakCapacityStatusDTO> list = peakCapacityService.listLimitsWithUsage();
        PeakCapacityStatusDTO row = list.stream()
                .filter(l -> "T2航站楼".equals(l.getTerminalArea()) && "母婴室".equals(l.getDeviceType()))
                .findFirst().orElseThrow();
        assertEquals(3, row.getMaxConcurrent());
        assertEquals(2, row.getCurrentUsage());
    }
}
