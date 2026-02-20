package auto.annotate.common.repository;

import auto.annotate.common.entity.ExcelJobLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ExcelJobLogRepository extends JpaRepository<ExcelJobLog, UUID> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ExcelJobLog l where l.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
