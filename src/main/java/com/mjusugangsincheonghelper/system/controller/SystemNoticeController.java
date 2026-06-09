package com.mjusugangsincheonghelper.system.controller;

import com.mjusugangsincheonghelper.global.annotation.OperationErrorCodes;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.envelope.SingleSuccessResponseEnvelope;
import com.mjusugangsincheonghelper.system.dto.NoticeDto;
import com.mjusugangsincheonghelper.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Tag(name = "System", description = "시스템 설정 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{version}/system/notices")
public class SystemNoticeController {

	private final SystemConfigService systemConfigService;
	private final ObjectMapper objectMapper;

	@GetMapping(version = "1+")
	@Operation(
			summary = "Get notices",
			description = "공지사항 목록을 조회합니다.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "조회 성공"
					)
			}
	)
	@OperationErrorCodes({
			ErrorCode.GLOBAL_INTERNAL_SERVER_ERROR
	})
	public ResponseEntity<SingleSuccessResponseEnvelope<List<NoticeDto>>> getNotices() {
		String raw = systemConfigService.getRaw("notices");
		List<NoticeDto> notices = parseNotices(raw);
		return ResponseEntity.ok(SingleSuccessResponseEnvelope.of(notices));
	}

	private List<NoticeDto> parseNotices(String raw) {
		try {
			return objectMapper.readValue(raw, new TypeReference<List<NoticeDto>>() {});
		} catch (Exception e) {
			return List.of();
		}
	}
}
