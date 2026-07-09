package com.airport.maternity.repository;

import com.airport.maternity.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}