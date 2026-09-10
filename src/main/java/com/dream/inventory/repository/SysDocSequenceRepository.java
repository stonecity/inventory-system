package com.dream.inventory.repository;

import com.dream.inventory.entity.SysDocSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface SysDocSequenceRepository extends JpaRepository<SysDocSequence, SysDocSequence.SysDocSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SysDocSequence s WHERE s.prefix = :prefix AND s.bizDate = :bizDate")
    Optional<SysDocSequence> findForUpdate(@Param("prefix") String prefix, @Param("bizDate") LocalDate bizDate);
}
