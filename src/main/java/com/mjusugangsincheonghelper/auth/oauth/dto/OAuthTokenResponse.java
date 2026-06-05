package com.mjusugangsincheonghelper.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OAuthTokenResponse {
    private Long memberId;
    private String role;
    private String name;
    private String position;
    private String department;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String accessToken;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String refreshToken;
}
