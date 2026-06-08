package com.mjusugangsincheonghelper.auth.merge;

import com.mjusugangsincheonghelper.auth.session.token.TokenProvider;
import io.jsonwebtoken.Claims;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MergeTicketService {

	private final TokenProvider tokenProvider;

	public String createTicket(Long guestMemberId, String googleSubId) {
		return tokenProvider.createMergeTicket(guestMemberId, googleSubId);
	}

	public MergeTicketClaims consume(String ticket) {
		Claims claims;
		try {
			claims = tokenProvider.parseMergeTicket(ticket);
		} catch (Exception e) {
			throw new BaseException(ErrorCode.AUTH_MERGE_TICKET_EXPIRED);
		}
		return new MergeTicketClaims(
				Long.parseLong(claims.getSubject()),
				claims.get("googleSubId", String.class)
		);
	}

	public record MergeTicketClaims(Long guestMemberId, String googleSubId) {
	}
}
