package com.example.fileshare.common.actor;

/**
 * 요청한 사용자를 서비스 계층에 전달하는 값이다. 인증 프레임워크 타입과 분리해 서비스가 보안 구현에 묶이지 않게 한다.
 */
public record Actor(
        Long userId,
        boolean admin
) {
}
