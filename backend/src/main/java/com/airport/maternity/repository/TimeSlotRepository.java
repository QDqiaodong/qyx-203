package com.airport.maternity.repository;

import com.airport.maternity.entity.TimeSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    Page<TimeSlot> findByDeviceId(Long deviceId, Pageable pageable);

    List<TimeSlot> findByDeviceId(Long deviceId);

    @Query("SELECT DISTINCT t.deviceId FROM TimeSlot t WHERE t.startTime <= :endTime AND t.endTime >= :startTime")
    List<Long> findDeviceIdsByTimeRange(@Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime);

    @Query("SELECT t FROM TimeSlot t WHERE t.startTime <= :endTime AND t.endTime >= :startTime")
    List<TimeSlot> findByTimeRange(@Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime);

    @Query("SELECT t FROM TimeSlot t, Device d WHERE t.deviceId = d.id " +
            "AND d.terminalArea = :terminalArea AND d.deviceType = :deviceType AND t.status = :status")
    List<TimeSlot> findByAreaAndTypeAndStatus(@Param("terminalArea") String terminalArea,
                                              @Param("deviceType") String deviceType,
                                              @Param("status") String status);
}