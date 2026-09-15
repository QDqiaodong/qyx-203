package com.airport.maternity.repository;

import com.airport.maternity.entity.PeakWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PeakWindowRepository extends JpaRepository<PeakWindow, Long> {

    List<PeakWindow> findAllByOrderByStartTimeAsc();
}
