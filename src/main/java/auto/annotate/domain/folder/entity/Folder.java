package auto.annotate.domain.folder.entity;

import auto.annotate.common.entity.Timestamped;
import auto.annotate.domain.document.entity.Document;
import auto.annotate.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "Folder")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Folder extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "folder", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Document> documents = new ArrayList<>();

    public Folder(String name, User user){
        this.name = name;
        this.user = user;
    }

    public void update(String name) {
        this.name = name;
    }

    public void delete() {
        this.deletedAt =  LocalDateTime.now();
    }
}