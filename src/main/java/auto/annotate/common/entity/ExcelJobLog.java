package auto.annotate.common.entity;

import auto.annotate.common.enums.JobStatus;
import auto.annotate.domain.document.dto.HighlightTarget;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "excel_job_log",
        indexes = {
                @Index(name = "idx_excel_job_log_created_at", columnList = "createdAt"),
                @Index(name = "idx_excel_job_log_document_id", columnList = "documentId"),
                @Index(name = "idx_excel_job_log_status", columnList = "status"),
                @Index(name = "idx_excel_job_log_user_id", columnList = "userId")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExcelJobLog extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID documentId;

    @Column(nullable = false, length = 128)
    private String bundleKey;

    @Column(nullable = false, columnDefinition = "text")
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private HighlightTarget target;

    @Column(length = 200)
    private String requestPath;

    @Column(length = 8)
    private String httpMethod;

    @Column(length = 64)
    private String instanceId; // hostname or instance-id

    @Column(nullable = false)
    private int condition;

    @Column(nullable = false, length = 64)
    private String outTag;

    @Column(nullable = false, length = 16)
    private String runId;

    @Column
    private Integer pageCount;

    @Column
    private Integer rowsTotal;

    @Column
    private Integer hitsCount;

    @Column
    private Long parseMs;

    @Column
    private Long computeMs;

    @Column
    private Long writeMs;

    @Column
    private Long totalMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    @Column(length = 64)
    private String errorEnum;     // ExceptionEnum name

    @Column(length = 128)
    private String errorType;     // Exception class simple name

    @Column(length = 500)
    private String errorMessage;  // trimmed message
}
