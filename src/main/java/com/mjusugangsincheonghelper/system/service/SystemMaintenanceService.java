package com.mjusugangsincheonghelper.system.service;

import com.mjusugangsincheonghelper.database.repository.SystemRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMaintenanceService {

	private final SystemRepository systemRepository;

	/**
	 * 만료된 기기 세션(세션 만료 시각이 지난 기기, Firebase Cloud Messaging 토큰 포함)을 일괄 삭제한다.
	 * 관리자 정리 버튼용: 삭제된 개수를 반환한다.
	 */
	@Transactional
	public long cleanupExpiredDevices() {
		long deletedCount = systemRepository.deleteExpiredDevices(Instant.now());
		log.info("Cleaned up expired device sessions. deletedCount={}", deletedCount);
		return deletedCount;
	}
}
