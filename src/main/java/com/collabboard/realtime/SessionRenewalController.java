package com.collabboard.realtime;

import com.collabboard.security.AccessTokenCutoff;
import com.collabboard.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Açık canlı bağlantıya yeni erişim jetonunu bildirmek (ADR 0025).
 *
 * Bağlantının ömrü CONNECT'teki jetona bağlı; süresi geçince bağlantı kapanır
 * (RealtimeSessions). Arayüz jetonunu her yenilediğinde yenisini buraya da
 * gönderir; böylece meşru kullanıcının bağlantısı hiç kopmaz, yalnızca jetonunu
 * yenileyemeyen (oturumu kapatılmış, şifresi değişmiş) bağlantılar kapanır.
 *
 * Jeton yalnızca bağlantının sahibine aitse ve şifre değişiminden sonra
 * verilmişse kabul edilir. Geçersiz jeton sessizce yok sayılır: bağlantı eski
 * bitiş anında kapanır.
 */
@Controller
public class SessionRenewalController {

    private static final Logger log = LoggerFactory.getLogger(SessionRenewalController.class);

    /** İstemcinin gönderdiği gövde: {"token": "<erişim jetonu>"}. */
    public record Renewal(String token) {
    }

    private final JwtTokenProvider tokenProvider;
    private final AccessTokenCutoff cutoff;
    private final RealtimeSessions sessions;

    public SessionRenewalController(JwtTokenProvider tokenProvider, AccessTokenCutoff cutoff,
                                    RealtimeSessions sessions) {
        this.tokenProvider = tokenProvider;
        this.cutoff = cutoff;
        this.sessions = sessions;
    }

    @MessageMapping("/session/renew")
    public void renew(@Payload Renewal renewal, SimpMessageHeaderAccessor headers) {
        Principal user = headers.getUser();
        if (user == null || renewal == null) {
            return;
        }
        tokenProvider.parseValid(renewal.token())
                .filter(claims -> user.getName().equals(claims.getSubject()))
                .filter(claims -> !cutoff.isCutOff(claims.getSubject(), claims.getIssuedAt()))
                .ifPresentOrElse(
                        claims -> sessions.renew(headers.getSessionId(), user.getName(),
                                claims.getExpiration().toInstant()),
                        () -> log.debug("Canlı bağlantı için geçersiz jeton yenilemesi yok sayıldı"));
    }
}
