package com.toolpick.user;

/**
 * 사용자 역할. [CS] 역할 기반 접근 제어(RBAC): 사용자마다 권한을 따로 주지 않고 역할에 권한을 묶는다.
 * ADMIN은 SecurityConfig에서 /api/admin/** 에 접근할 수 있다.
 */
public enum Role {
    USER, ADMIN
}
