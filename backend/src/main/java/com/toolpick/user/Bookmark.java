package com.toolpick.user;

import com.toolpick.repo.Repo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 북마크 (bookmark 테이블). 사용자와 레포 사이의 다대다(N:M) 관계를 잇는 연결 테이블이다.
 * <p>
 * [CS] 다대다 관계: 한 사용자가 여러 레포를, 한 레포를 여러 사용자가 북마크한다. 관계형 DB는 다대다를 직접 표현하지 못해서
 * (user_id, repo_id) 쌍을 행으로 저장하는 연결 테이블을 둔다. UNIQUE(user_id, repo_id)로 같은 북마크가 두 번 생기지 않는다.
 * 연결 테이블에 created_at 같은 정보를 더 담을 수 있어 별도 엔티티로 만들었다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id")
    private Repo repo;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static Bookmark of(AppUser user, Repo repo) {
        Bookmark b = new Bookmark();
        b.user = user;
        b.repo = repo;
        b.createdAt = Instant.now();
        return b;
    }
}
