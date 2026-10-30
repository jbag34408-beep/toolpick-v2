package com.toolpick.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 회원 DB 접근. 로그인 정보에는 GitHub id가 들어 있으므로 그것으로 회원을 찾는다 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByGithubId(Long githubId);
}
