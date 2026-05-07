package com.booking.service.repository;

import com.booking.service.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA репозиторий для журнала обработанных событий.
 * Используется для обеспечения идемпотентности обработки сообщений из RabbitMQ.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
