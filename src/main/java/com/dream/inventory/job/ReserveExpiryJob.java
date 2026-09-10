package com.dream.inventory.job;

import com.dream.inventory.entity.StockMovement;
import com.dream.inventory.entity.enums.MovementStatus;
import com.dream.inventory.entity.enums.MovementType;
import com.dream.inventory.repository.StockMovementRepository;
import com.dream.inventory.service.SettingsService;
import com.dream.inventory.service.StockMovementService;
import com.dream.inventory.dto.movement.VersionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReserveExpiryJob {

    private final StockMovementRepository movementRepository;
    private final StockMovementService movementService;
    private final SettingsService settingsService;

    @Scheduled(fixedRate = 3_600_000)
    public void expireReservedOrders() {
        int hours = Integer.parseInt(settingsService.getAll().getOrDefault("reserve.expire.hours", "48"));
        Instant cutoff = Instant.now().minus(hours, ChronoUnit.HOURS);
        List<StockMovement> expired = movementRepository.findByStatusAndTypeAndSubmittedAtBefore(
                MovementStatus.RESERVED, MovementType.SALE_OUT, cutoff);
        for (StockMovement m : expired) {
            try {
                VersionRequest req = new VersionRequest();
                req.setVersion(m.getVersion());
                req.setReason("预留超时自动取消");
                movementService.cancel(m.getId(), req);
                log.info("Auto-cancelled expired reserve: {}", m.getMovementNo());
            } catch (Exception e) {
                log.warn("Failed to auto-cancel {}: {}", m.getMovementNo(), e.getMessage());
            }
        }
    }
}
