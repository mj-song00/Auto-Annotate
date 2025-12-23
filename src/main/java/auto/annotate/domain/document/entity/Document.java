package auto.annotate.domain.document.entity;

import auto.annotate.domain.document.dto.HighlightTarget;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Table(name = "document")
@NoArgsConstructor
public class Document {
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
    @Column(name = "target", length = 30) // nullable로 시작(마이그레이션 편하게)
    private HighlightTarget target;

    public Document(String originalFileName,  String fileUrl, String bundleKey, HighlightTarget target) {
        this.originalFileName = originalFileName;
        this.fileUrl = fileUrl;
        this.bundleKey = bundleKey;
        this.target = target;
    }
}
