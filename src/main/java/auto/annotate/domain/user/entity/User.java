package auto.annotate.domain.user.entity;

import auto.annotate.common.entity.Timestamped;
import auto.annotate.domain.document.entity.Document;
import auto.annotate.domain.user.enums.UserRole;
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
@Table(name = "Users")
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED) // 외부 직접 호출을 막기 위해 protected 설정
public class User extends Timestamped {

    @Id
    @Column(columnDefinition = "BINARY(16)", nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nickName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole userRole; // 사용자 역할

    @Column
    private LocalDateTime deletedAt;


    @OneToMany(mappedBy = "user", orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Document> documents = new ArrayList<>();


}
