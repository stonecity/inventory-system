package com.dream.inventory.job;

import com.dream.inventory.entity.Stocktake;
import com.dream.inventory.entity.enums.StocktakeStatus;
import com.dream.inventory.repository.StocktakeRepository;
import com.dream.inventory.service.SettingsService;
import com.dream.inventory.service.StocktakeService;
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
public class StocktakeTimeoutJob {

    private final StocktakeRepository stocktakeRepository;
    private final StocktakeService stocktakeService;
    private final SettingsService settingsService;

    @Scheduled(fixedRate = 3_600_000)
    public void checkTimeout() {
        int warnHours = Integer.parseInt(settingsService.getAll().getOrDefault("stocktake.warn.hours", "72"));
        int cancelDays = Integer.parseInt(settingsService.getAll().getOrDefault("stocktake.auto-cancel.days", "7"));
        List<StocktakeStatus> open = List.of(StocktakeStatus.LOCKED, StocktakeStatus.COUNTING);
        Instant cancelCutoff = Instant.now().minus(cancelDays, ChronoUnit.DAYS);
        Instant warnCutoff = Instant.now().minus(warnHours, ChronoUnit.HOURS);

        for (Stocktake st : stocktakeRepository.findByStatusInAndSnapshotAtBefore(open, cancelCutoff)) {
            try {
                stocktakeService.cancel(st.getId());
                log.warn("Auto-cancelled timed-out stocktake {}", st.getStocktakeNo());
            } catch (Exception e) {
                log.warn("Failed to auto-cancel stocktake {}: {}", st.getStocktakeNo(), e.getMessage());
            }
        }
        for (Stocktake st : stocktakeRepository.findByStatusInAndSnapshotAtBefore(open, warnCutoff)) {
            if (st.getStatus() == StocktakeStatus.CANCELLED) {
                continue;
            }
            log.warn("Stocktake {} locked/counting over {} hours", st.getStocktakeNo(), warnHours);
        }
    }
}
