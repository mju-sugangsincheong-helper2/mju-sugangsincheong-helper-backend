package com.mjusugangsincheonghelper.auth.oauth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class OAuthStartResponse {
    private String googleAuthUrl;
}
