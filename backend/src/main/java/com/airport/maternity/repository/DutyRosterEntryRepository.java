package com.airport.maternity.repository;

import com.airport.maternity.entity.DutyRosterEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DutyRosterEntryRepository extends JpaRepository<DutyRosterEntry, Long> {

    /** 当班日名单（含撤下留痕），按登记顺序倒序 */
    List<DutyRosterEntry> findByDutyDate(LocalDate dutyDate, Sort sort);

    /** 当班日仍挂在可用名单上的行 */
    List<DutyRosterEntry> findByDutyDateAndStatus(LocalDate dutyDate, String status, Sort sort);

    Optional<DutyRosterEntry> findByDeviceIdAndDutyDateAndStatus(
            Long deviceId, LocalDate dutyDate, String status);

    /**
     * 行级悲观锁：校准撤名单与名单挂入/撤下并发时串行化，
     * 不会出现一边撤下、一边又挂成当班可用的交叉状态。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM DutyRosterEntry r WHERE r.id = :id")
    Optional<DutyRosterEntry> findByIdForUpdate(@Param("id") Long id);
}
