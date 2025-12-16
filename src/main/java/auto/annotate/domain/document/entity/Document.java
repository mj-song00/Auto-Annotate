package auto.annotate.domain.document.entity;

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

    public Document(String originalFileName,  String fileUrl) {
        this.originalFileName = originalFileName;
        this.fileUrl = fileUrl;
    }
}
