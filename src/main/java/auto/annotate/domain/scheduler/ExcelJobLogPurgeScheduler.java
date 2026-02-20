package auto.annotate.domain.scheduler;

import auto.annotate.common.repository.ExcelJobLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelJobLogPurgeScheduler {
    private static final int RETENTION_DAYS = 30;
    private final ExcelJobLogRepository excelJobLogRepository;

    @Transactional
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void purgeOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = excelJobLogRepository.deleteOlderThan(cutoff);
        log.info("[EXCEL_JOB_LOG_PURGE] retentionDays={} cutoff={} deleted={}",
                RETENTION_DAYS, cutoff, deleted);
    }
}
