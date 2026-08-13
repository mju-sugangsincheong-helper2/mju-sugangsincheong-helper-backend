package com.mjusugangsincheonghelper.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder @AllArgsConstructor
public class OAuthTokenResponse {
    private final String status;
    private final Boolean newUser;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Long memberId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String role;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String position;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String department;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String sessionAccessToken;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String sessionRefreshToken;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String mergeTicket;
}
