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

	public String createTicket(Long guestMemberId, Long targetMemberId) {
		return tokenProvider.createMergeTicket(guestMemberId, targetMemberId);
	}

	public MergeTicketClaims consume(String ticket) {
		Claims claims;
		try {
			claims = tokenProvider.parseMergeTicket(ticket);
		} catch (Exception e) {
			throw new BaseException(ErrorCode.AUTH_MERGE_TICKET_EXPIRED, e);
		}
		return new MergeTicketClaims(
				Long.parseLong(claims.getSubject()),
				claims.get("targetMemberId", Long.class)
		);
	}

	public record MergeTicketClaims(Long guestMemberId, Long targetMemberId) {
	}
}
