package com.airport.maternity.repository;

import com.airport.maternity.entity.TimeSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    Page<TimeSlot> findByDeviceId(Long deviceId, Pageable pageable);

    List<TimeSlot> findByDeviceId(Long deviceId);

    /**
     * 设备停用/删除等批量处置时锁住该设备名下全部时段行，
     * 与时段编辑互斥，避免一边时段写成生效中、一边设备已停用/删除。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TimeSlot t WHERE t.deviceId = :deviceId")
    List<TimeSlot> findByDeviceIdForUpdate(@Param("deviceId") Long deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TimeSlot t WHERE t.id = :id")
    Optional<TimeSlot> findByIdForUpdate(@Param("id") Long id);

    List<TimeSlot> findByDeviceIdAndStatus(Long deviceId, String status);

    @Query("SELECT DISTINCT t.deviceId FROM TimeSlot t WHERE t.startTime <= :endTime AND t.endTime >= :startTime")
    List<Long> findDeviceIdsByTimeRange(@Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime);

    @Query("SELECT t FROM TimeSlot t WHERE t.startTime <= :endTime AND t.endTime >= :startTime")
    List<TimeSlot> findByTimeRange(@Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime);

    @Query("SELECT t FROM TimeSlot t, Device d WHERE t.deviceId = d.id " +
            "AND d.status = '正常' " +
            "AND d.terminalArea = :terminalArea AND d.deviceType = :deviceType AND t.status = :status")
    List<TimeSlot> findByAreaAndTypeAndStatus(@Param("terminalArea") String terminalArea,
                                              @Param("deviceType") String deviceType,
                                              @Param("status") String status);

    /**
     * 统计页「按时段筛选设备」的唯一数据源：
     * 只算正常在用设备（设备状态 = 正常）名下、生效中、查询日仍在日期范围内、
     * 且每日时段与查询区间相交的占用。停用设备、已失效/已停用时段、历史时段均不命中。
     */
    @Query("SELECT DISTINCT t.deviceId FROM TimeSlot t, Device d WHERE t.deviceId = d.id " +
            "AND d.status = '正常' AND t.status = '生效中' " +
            "AND t.startDate <= :date AND t.endDate >= :date " +
            "AND t.startTime <= :endTime AND t.endTime >= :startTime")
    List<Long> findInUseDeviceIds(@Param("startTime") LocalTime startTime,
                                  @Param("endTime") LocalTime endTime,
                                  @Param("date") LocalDate date);
}
