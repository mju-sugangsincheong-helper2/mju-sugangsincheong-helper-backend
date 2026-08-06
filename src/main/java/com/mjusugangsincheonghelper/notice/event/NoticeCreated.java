package com.mjusugangsincheonghelper.notice.event;

/**
 * 공지 등록 이벤트. 커밋 후 {@link NoticeEventListener}가 전체 푸시를 발행한다.
 */
public record NoticeCreated(Long noticeId, String title) {
}
