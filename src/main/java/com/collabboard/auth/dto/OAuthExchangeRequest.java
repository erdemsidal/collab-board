package com.collabboard.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Google dönüşünde adrese yazılan tek kullanımlık kod (/?oauth=...). */
public record OAuthExchangeRequest(
        @NotBlank(message = "Giriş kodu boş olamaz")
        String code
) {
}
