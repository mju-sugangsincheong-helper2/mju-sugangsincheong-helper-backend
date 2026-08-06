package com.mjusugangsincheonghelper.system.dto;

/**
 * 만료된 기기 세션(FCM 토큰) 정리 결과.
 * @param cleared 삭제된 기기(만료 세션) 개수
 */
public record DeviceCleanupResult(long cleared) {
}