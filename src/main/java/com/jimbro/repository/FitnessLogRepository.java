package com.jimbro.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jimbro.entity.FitnessLog;
import java.util.List;

@Repository
public interface FitnessLogRepository extends JpaRepository<FitnessLog, Long> {
    List<FitnessLog> findByUserIdOrderByTimestampDesc(Long userId);
}
