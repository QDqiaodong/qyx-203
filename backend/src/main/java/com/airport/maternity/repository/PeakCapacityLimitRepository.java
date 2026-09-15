package com.airport.maternity.repository;

import com.airport.maternity.entity.PeakCapacityLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PeakCapacityLimitRepository extends JpaRepository<PeakCapacityLimit, Long> {

    Optional<PeakCapacityLimit> findByTerminalAreaAndDeviceType(String terminalArea, String deviceType);

    /**
     * 悲观写锁读取上限配置：加绑/改时段/调区时先锁住该「分区+类型」的上限行，
     * 保证并发加绑时只有一个事务能占用最后的高峰名额。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM PeakCapacityLimit l WHERE l.terminalArea = :terminalArea AND l.deviceType = :deviceType")
    Optional<PeakCapacityLimit> findByAreaAndTypeForUpdate(@Param("terminalArea") String terminalArea,
                                                           @Param("deviceType") String deviceType);

    List<PeakCapacityLimit> findAllByOrderByTerminalAreaAscDeviceTypeAsc();
}
