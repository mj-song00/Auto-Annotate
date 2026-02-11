package auto.annotate.domain.document.entity;

import auto.annotate.common.entity.Timestamped;
import auto.annotate.domain.document.dto.HighlightTarget;
import auto.annotate.domain.folder.entity.Folder;
import auto.annotate.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "document")
@NoArgsConstructor
public class Document extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column
    private String originalFileName;

    @Column
    private String fileUrl;

    @Column(name = "bundle_key", length = 36)
    private String bundleKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", length = 30)
    private HighlightTarget target;

    @Column
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private Folder folder;

    public Document(String originalFileName, String fileUrl, String bundleKey,
                    HighlightTarget target, User user, Folder folder) {
        this.originalFileName = originalFileName;
        this.fileUrl = fileUrl;
        this.bundleKey = bundleKey;
        this.target = target;
        this.deletedAt = null;
        this.user = user;
        this.folder = folder;
    }
}
