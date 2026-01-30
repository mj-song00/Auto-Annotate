package auto.annotate.domain.folder.entity;

import auto.annotate.common.entity.Timestamped;
import auto.annotate.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "Folder")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED) // 외부 직접 호출을 막기 위해 protected 설정
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

    public Folder(String name, User user){
        this.name = name;
        this.user = user;
    }
}
