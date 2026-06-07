package com.mjusugangsincheonghelper.auth.oauth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OAuthTokenResponse {
    private String status;
    private Boolean newUser;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long memberId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String role;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String position;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String department;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String accessToken;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String refreshToken;

    public static OAuthTokenResponse existingMember(Long memberId, String role, String name, String position, String department) {
        return OAuthTokenResponse.builder()
                .status("SUCCESS")
                .newUser(false)
                .memberId(memberId)
                .role(role)
                .name(name)
                .position(position)
                .department(department)
                .build();
    }
}
