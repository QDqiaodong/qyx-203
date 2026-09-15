package com.airport.maternity.repository;

import com.airport.maternity.entity.Device;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceCode(String deviceCode);

    Page<Device> findByTerminalArea(String terminalArea, Pageable pageable);

    Page<Device> findByDeviceType(String deviceType, Pageable pageable);

    Page<Device> findByTerminalAreaAndDeviceType(String terminalArea, String deviceType, Pageable pageable);

    List<Device> findAll();

    /**
     * 设备行悲观锁：停用/删除与时段编辑并发时，
     * 保证同一台设备不会出现「时段刚写成生效中、设备却已停用/删除」。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Device d WHERE d.id = :id")
    Optional<Device> findByIdForUpdate(@Param("id") Long id);
}
