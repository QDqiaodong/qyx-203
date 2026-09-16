package com.airport.maternity.repository;

import com.airport.maternity.entity.OpeningInspection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OpeningInspectionRepository extends JpaRepository<OpeningInspection, Long> {

    Page<OpeningInspection> findByDeviceId(Long deviceId, Pageable pageable);

    Page<OpeningInspection> findByInspectionDate(LocalDate inspectionDate, Pageable pageable);

    Page<OpeningInspection> findByDeviceIdAndInspectionDate(Long deviceId, LocalDate inspectionDate, Pageable pageable);

    /** 当天最近一次巡检（id 最大即最新登记） */
    Optional<OpeningInspection> findTopByDeviceIdAndInspectionDateOrderByIdDesc(Long deviceId, LocalDate inspectionDate);

    /** 当天全部巡检记录：在内存里按设备归并出各台最近一次结论 */
    List<OpeningInspection> findByInspectionDate(LocalDate inspectionDate);
}
