package com.airport.maternity;

import com.airport.maternity.dto.CalibrationDTO;
import com.airport.maternity.dto.DeviceDTO;
import com.airport.maternity.dto.DutyRosterAddDTO;
import com.airport.maternity.entity.CalibrationRecord;
import com.airport.maternity.entity.Device;
import com.airport.maternity.entity.DutyRosterEntry;
import com.airport.maternity.exception.CalibrationConflictException;
import com.airport.maternity.exception.CalibrationNotPassedException;
import com.airport.maternity.repository.CalibrationRecordRepository;
import com.airport.maternity.repository.DeviceRepository;
import com.airport.maternity.repository.DutyRosterEntryRepository;
import com.airport.maternity.service.CalibrationService;
import com.airport.maternity.service.DeviceService;
import com.airport.maternity.service.DutyRosterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 体温枪校准登记与当班可用名单联动的端到端验证：
 * 1) 校准通过：名单里的枪继续留着；
 * 2) 校准不通过：当班名单当场撤下、原因写明就是这次校准没过，且当天不能再挂回；
 * 3) 两名值机并发给同一把枪写结论：只留先落地那份，后到 409 冲突、不改不盖；
 * 4) 校验非法结论（模拟校准没写完）事务回滚：名单仍是动手前的样子，
 *    不会出现枪已从名单消失、校准记录却没有。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CalibrationIntegrationTest {

    @Autowired
    private CalibrationService calibrationService;

    @Autowired
    private DutyRosterService dutyRosterService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private CalibrationRecordRepository calibrationRepository;

    @Autowired
    private DutyRosterEntryRepository rosterRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    private Device newGun(String code) {
        DeviceDTO dto = new DeviceDTO();
        dto.setDeviceCode(code);
        dto.setDeviceType("体温枪");
        dto.setTerminalArea("T1值机岛");
        dto.setStatus("正常");
        return deviceService.save(dto).getDevice();
    }

    private DutyRosterEntry putOnDuty(Long deviceId) {
        DutyRosterAddDTO dto = new DutyRosterAddDTO();
        dto.setDeviceId(deviceId);
        dto.setOperator("当班值机");
        return dutyRosterService.addToRoster(dto);
    }

    private CalibrationDTO calibrationDto(Long deviceId, String calibrator, String result) {
        CalibrationDTO dto = new CalibrationDTO();
        dto.setDeviceId(deviceId);
        dto.setCalibrator(calibrator);
        dto.setResult(result);
        dto.setRemark(result.equals(CalibrationService.RESULT_FAIL) ? "读数偏差超限" : null);
        return dto;
    }

    private List<DutyRosterEntry> activeRoster(Long deviceId) {
        return rosterRepository.findByDeviceIdAndDutyDateAndStatus(
                deviceId, LocalDate.now(), DutyRosterEntry.STATUS_ON_DUTY)
                .stream().toList();
    }

    @Test
    void 校准通过_当班名单继续保留() {
        Device gun = newGun("GUN-PASS-01");
        putOnDuty(gun.getId());

        var result = calibrationService.save(
                calibrationDto(gun.getId(), "值机甲", CalibrationService.RESULT_PASS));

        assertEquals(CalibrationService.RESULT_PASS, result.getCalibration().getResult());
        assertNull(result.getRemovedRosterEntry(), "校准通过不撤名单");
        // 名单上继续留着这把枪
        assertEquals(1, activeRoster(gun.getId()).size());
    }

    @Test
    void 校准不通过_当班名单当场撤下且写明原因_当天不能再挂回() {
        Device gun = newGun("GUN-FAIL-01");
        DutyRosterEntry entry = putOnDuty(gun.getId());
        assertEquals(1, activeRoster(gun.getId()).size());

        var result = calibrationService.save(
                calibrationDto(gun.getId(), "值机甲", CalibrationService.RESULT_FAIL));

        // 返回的撤下行就是原本当班那条，且原因写明是这次校准没过
        DutyRosterEntry removed = result.getRemovedRosterEntry();
        assertNotNull(removed);
        assertEquals(entry.getId(), removed.getId());
        assertEquals(DutyRosterEntry.STATUS_REMOVED, removed.getStatus());
        assertEquals(result.getCalibration().getId(), removed.getCalibrationId());
        assertNotNull(removed.getRemoveReason());
        assertTrue(removed.getRemoveReason().contains(DutyRosterEntry.REMOVE_REASON_CALIBRATION));
        assertTrue(removed.getRemoveReason().contains("值机甲"));

        // 当班可用名单里不再出现这把枪
        assertEquals(0, activeRoster(gun.getId()).size());

        // 已撤下的枪不能再挂回当班名单
        assertThrows(CalibrationNotPassedException.class, () -> putOnDuty(gun.getId()));
        assertEquals(0, activeRoster(gun.getId()).size());
    }

    @Test
    void 校准不通过时枪本就不在名单_只留记录不新增撤下行() {
        Device gun = newGun("GUN-FAIL-02");

        var result = calibrationService.save(
                calibrationDto(gun.getId(), "值机甲", CalibrationService.RESULT_FAIL));

        assertNotNull(result.getCalibration());
        assertNull(result.getRemovedRosterEntry());
        // 同样不能再挂入当班名单
        assertThrows(CalibrationNotPassedException.class, () -> putOnDuty(gun.getId()));
    }

    @Test
    void 同枪同日重复登记_后到的拒绝且不能改先写的结论() {
        Device gun = newGun("GUN-DUP-01");
        calibrationService.save(calibrationDto(gun.getId(), "值机甲", CalibrationService.RESULT_PASS));

        // 后到的想改成不通过：拒绝
        CalibrationConflictException ex = assertThrows(CalibrationConflictException.class,
                () -> calibrationService.save(
                        calibrationDto(gun.getId(), "值机乙", CalibrationService.RESULT_FAIL)));
        assertTrue(ex.getMessage().contains("值机甲"));
        assertTrue(ex.getMessage().contains("先"));

        // 库里只有一份，且是先落地的「通过」
        List<CalibrationRecord> records = calibrationRepository
                .findAll()
                .stream()
                .filter(r -> r.getDeviceId().equals(gun.getId()))
                .toList();
        assertEquals(1, records.size());
        assertEquals(CalibrationService.RESULT_PASS, records.get(0).getResult());
        assertEquals("值机甲", records.get(0).getCalibrator());
    }

    @Test
    void 并发抢写同一把枪_只落先写者一份结论() throws Exception {
        Device gun = newGun("GUN-RACE-01");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger pass = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        // 两个校准人同时提交，一个写通过、一个写不通过
        java.util.concurrent.Callable<String> taskPass = () -> {
            ready.countDown();
            go.await();
            try {
                calibrationService.save(
                        calibrationDto(gun.getId(), "并发值机A", CalibrationService.RESULT_PASS));
                pass.incrementAndGet();
                return "OK-PASS";
            } catch (CalibrationConflictException e) {
                conflict.incrementAndGet();
                return "CONFLICT";
            } catch (Exception e) {
                return "ERROR:" + e.getClass().getSimpleName();
            }
        };
        java.util.concurrent.Callable<String> taskFail = () -> {
            ready.countDown();
            go.await();
            try {
                calibrationService.save(
                        calibrationDto(gun.getId(), "并发值机B", CalibrationService.RESULT_FAIL));
                pass.incrementAndGet();
                return "OK-FAIL";
            } catch (CalibrationConflictException e) {
                conflict.incrementAndGet();
                return "CONFLICT";
            } catch (Exception e) {
                return "ERROR:" + e.getClass().getSimpleName();
            }
        };

        Future<String> f1 = pool.submit(taskPass);
        Future<String> f2 = pool.submit(taskFail);
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // 只有一份落库，另一个冲突，绝不出现两份
        assertEquals(1, pass.get());
        assertEquals(1, conflict.get());
        List<CalibrationRecord> records = calibrationRepository
                .findAll()
                .stream()
                .filter(r -> r.getDeviceId().equals(gun.getId()))
                .toList();
        assertEquals(1, records.size());
    }

    @Test
    void 校准登记非法结论中途失败_事务回滚_名单仍是动手前样子() {
        Device gun = newGun("GUN-ROLLBACK-01");
        putOnDuty(gun.getId());
        assertEquals(1, activeRoster(gun.getId()).size());

        // 非法结论：校验在动名单之前直接抛出，整个事务回滚
        assertThrows(IllegalArgumentException.class, () ->
                calibrationService.save(calibrationDto(gun.getId(), "值机甲", "待复核")));

        // 校准记录一条都没有
        assertTrue(calibrationRepository
                .findByDeviceIdAndDutyDate(gun.getId(), LocalDate.now())
                .isEmpty());
        // 名单仍是动手前的样子：枪还在当班可用，没有「枪消失了、记录却没有」
        assertEquals(1, activeRoster(gun.getId()).size());
        DutyRosterEntry stillOnDuty = rosterRepository
                .findByDeviceIdAndDutyDateAndStatus(gun.getId(), LocalDate.now(), DutyRosterEntry.STATUS_ON_DUTY)
                .orElseThrow();
        assertNull(stillOnDuty.getCalibrationId());
        assertNull(stillOnDuty.getRemoveReason());
    }

    @Test
    void 设备停用_当天当班名单同事务撤下() {
        Device gun = newGun("GUN-DISABLE-01");
        putOnDuty(gun.getId());

        DeviceDTO edit = new DeviceDTO();
        edit.setId(gun.getId());
        edit.setDeviceCode(gun.getDeviceCode());
        edit.setDeviceType(gun.getDeviceType());
        edit.setTerminalArea(gun.getTerminalArea());
        edit.setStatus("停用");
        deviceService.save(edit);

        assertEquals(0, activeRoster(gun.getId()).size());
    }
}
