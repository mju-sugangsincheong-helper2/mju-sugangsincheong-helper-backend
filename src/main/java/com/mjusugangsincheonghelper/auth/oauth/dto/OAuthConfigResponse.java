package com.mjusugangsincheonghelper.auth.oauth.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OAuthConfigResponse {
    private String clientId;
    private List<String> scopes;
    private String redirectUri;
}
