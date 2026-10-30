package com.toolpick.user;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 북마크 DB 접근.
 * [문법] 쿼리 메서드 이름에서 UserId는 "user 필드의 id"로 해석된다(속성 경로 탐색).
 * existsBy…는 존재 여부만 확인하는 가벼운 쿼리, deleteBy…는 조건에 맞는 행을 지운다(트랜잭션 안에서 불러야 한다).
 */
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    /** 최근에 저장한 것부터. 목록에 레포 이름을 보여주므로 repo를 함께 JOIN한다(N+1 방지) */
    @EntityGraph(attributePaths = "repo")
    List<Bookmark> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndRepoId(Long userId, Long repoId);

    void deleteByUserIdAndRepoId(Long userId, Long repoId);
}
