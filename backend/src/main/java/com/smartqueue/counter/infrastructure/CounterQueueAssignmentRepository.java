package com.smartqueue.counter.infrastructure;

import com.smartqueue.counter.domain.CounterQueueAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CounterQueueAssignmentRepository extends JpaRepository<CounterQueueAssignment, UUID> {

    List<CounterQueueAssignment> findByCounterId(UUID counterId);

    List<CounterQueueAssignment> findByQueueId(UUID queueId);

    @Modifying
    @Query("DELETE FROM CounterQueueAssignment cqa WHERE cqa.counter.id = :counterId")
    void deleteByCounterId(@Param("counterId") UUID counterId);

    @Modifying
    @Query("DELETE FROM CounterQueueAssignment cqa WHERE cqa.queue.id = :queueId")
    void deleteByQueueId(@Param("queueId") UUID queueId);

    boolean existsByCounterIdAndQueueId(UUID counterId, UUID queueId);
}
