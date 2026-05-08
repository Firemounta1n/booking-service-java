package com.booking.service.repository;

import com.booking.service.entity.OutboxMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA репозиторий outbox-сообщений.
 */
@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * Возвращает не отправленные сообщения, у которых ещё не превышен лимит попыток.
     * FIFO: сортировка по {@code createdAt} от самых старых к новым.
     */
    @Query("""
            select m
            from OutboxMessage m
            where m.processedAt is null
              and m.attempts < :attemptsLimit
            order by m.createdAt asc, m.id asc
            """)
    List<OutboxMessage> findPending(@Param("attemptsLimit") int attemptsLimit, Pageable pageable);
}
