package com.airport.maternity.repository;

import com.airport.maternity.entity.ChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ChangeLogRepository extends JpaRepository<ChangeLog, Long> {

    Page<ChangeLog> findByDeviceId(Long deviceId, Pageable pageable);

    Page<ChangeLog> findByChangeTimeBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    Page<ChangeLog> findByDeviceIdAndChangeTimeBetween(Long deviceId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
}