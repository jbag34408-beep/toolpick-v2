package com.toolpick.repo;

/** 레포 id와 owner/name만 담은 가벼운 조회 결과 (규칙 필터용) */
public record RepoName(Long id, String fullName) {

    /** owner/name에서 name 부분 */
    public String name() {
        int slash = fullName.indexOf('/');
        return slash < 0 ? fullName : fullName.substring(slash + 1);
    }
}
