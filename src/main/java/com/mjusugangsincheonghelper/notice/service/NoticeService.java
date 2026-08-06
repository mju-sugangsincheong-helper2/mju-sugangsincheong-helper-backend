package com.mjusugangsincheonghelper.notice.service;

import com.mjusugangsincheonghelper.database.entity.NoticeEntity;
import com.mjusugangsincheonghelper.database.repository.NoticeRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.notice.dto.NoticeRequest;
import com.mjusugangsincheonghelper.notice.dto.NoticeResponse;
import com.mjusugangsincheonghelper.notice.event.NoticeCreated;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

	private final NoticeRepository noticeRepository;
	private final ApplicationEventPublisher eventPublisher;

	public List<NoticeResponse> findAll() {
		return noticeRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(NoticeResponse::from)
				.toList();
	}

	@Transactional
	public NoticeResponse create(NoticeRequest request) {
		NoticeEntity notice = noticeRepository.save(NoticeEntity.builder()
				.type(request.getType())
				.title(request.getTitle())
				.content(request.getContent())
				.build());

		// broadcast=true 일 때만 전체 사용자에게 푸시 (기본: 등록만).
		// 커밋 후(AFTER_COMMIT) 리스너가 발행하므로 롤백된 공지의 푸시가 나가지 않는다.
		if (Boolean.TRUE.equals(request.getBroadcast())) {
			eventPublisher.publishEvent(new NoticeCreated(notice.getId(), notice.getTitle()));
		}
		return NoticeResponse.from(notice);
	}

	@Transactional
	public NoticeResponse update(Long id, NoticeRequest request) {
		NoticeEntity notice = noticeRepository.findById(id)
				.orElseThrow(() -> new BaseException(ErrorCode.NOTICE_NOT_FOUND));
		notice.update(request.getType(), request.getTitle(), request.getContent());
		return NoticeResponse.from(notice);
	}

	@Transactional
	public void delete(Long id) {
		NoticeEntity notice = noticeRepository.findById(id)
				.orElseThrow(() -> new BaseException(ErrorCode.NOTICE_NOT_FOUND));
		noticeRepository.delete(notice);
	}
}