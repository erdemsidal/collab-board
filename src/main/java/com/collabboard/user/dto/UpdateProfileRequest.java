package com.collabboard.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Profilde değiştirilebilen bilgiler. PATCH: gönderilmeyen alan olduğu gibi kalır.
 *
 * Dil değiştirici yalnızca {"locale": "en"} gönderir; profil formu yalnızca adı.
 * Gönderilen ad boş olamaz (kalıp: en az bir boşluk dışı karakter); null ise
 * dokunulmaz. locale yalnızca desteklenen dillerden biri olabilir, kontrol
 * ProfileService'te (liste AppLocale'de tek yerde).
 *
 * E-posta burada YOK: hesabın kimliği, girişin anahtarı ve davetlerin
 * eşleştirildiği adres. Değiştirilebilmesi yeni adresin de doğrulanmasını
 * gerektirir; o ayrı bir akış.
 */
public record UpdateProfileRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "{validation.firstName.required}")
        @Size(max = 100, message = "{validation.firstName.max}")
        String firstName,

        @Pattern(regexp = "(?s).*\\S.*", message = "{validation.lastName.required}")
        @Size(max = 100, message = "{validation.lastName.max}")
        String lastName,

        String locale
) {
}
