package auto.annotate.domain.scheduler;

import auto.annotate.domain.folder.service.FolderServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FolderCleanupScheduler {
    private final FolderServiceImpl folderService;

    @Scheduled(cron = "0 5 0 * * *") // 매일 00:05
    public void deleteExpiredFolders() {
        folderService.deleteExpiredFolders();
    }
}
