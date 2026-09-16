package com.airport.maternity.repository;

import com.airport.maternity.entity.CalibrationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface CalibrationRecordRepository extends JpaRepository<CalibrationRecord, Long> {

    Page<CalibrationRecord> findByDeviceId(Long deviceId, Pageable pageable);

    Page<CalibrationRecord> findByDutyDate(LocalDate dutyDate, Pageable pageable);

    Page<CalibrationRecord> findByDeviceIdAndDutyDate(Long deviceId, LocalDate dutyDate, Pageable pageable);

    /**
     * 某把枪在某个当班日的校准结论。
     * 表上 (device_id, duty_date) 有唯一约束：同枪同日最多一份，先到先得。
     */
    Optional<CalibrationRecord> findByDeviceIdAndDutyDate(Long deviceId, LocalDate dutyDate);
}
