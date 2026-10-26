package com.collabboard.security;

import com.collabboard.common.tx.AfterCommit;
import com.collabboard.realtime.RealtimeSessions;
import com.collabboard.user.entity.User;
import org.springframework.stereotype.Service;

/**
 * "Her yerden çıkış": şifre değişince ve sıfırlanınca (ADR 0025).
 *
 * Üç şeyi birlikte yapar; biri eksik kalırsa kapı aralık kalır:
 *  1. Bütün refresh token'ları kapatır: yeni erişim jetonu alınamaz.
 *  2. Şimdiye kadar verilmiş erişim jetonlarını canlı bağlantı için geçersiz
 *     sayar: koparılan bağlantı eski jetonla geri gelemez.
 *  3. Açık canlı bağlantıları (bütün sunucularda) kapatır.
 *
 * Şifreyi değiştiren cihaz hemen yeni bir jeton çifti alır (ProfileService);
 * onun bağlantısı da kapanır ama birkaç saniye içinde yeni jetonla yeniden
 * bağlanır. REST istekleri için eski erişim jetonu en fazla ömrü (15 dk) kadar
 * geçerli kalır; bu kabul edilen bir boşluk.
 */
@Service
public class SessionTermination {

    private final RefreshTokenService refreshTokens;
    private final AccessTokenCutoff cutoff;
    private final RealtimeSessions realtimeSessions;

    public SessionTermination(RefreshTokenService refreshTokens, AccessTokenCutoff cutoff,
                              RealtimeSessions realtimeSessions) {
        this.refreshTokens = refreshTokens;
        this.cutoff = cutoff;
        this.realtimeSessions = realtimeSessions;
    }

    /** @return kapatılan oturum (refresh token ailesi) sayısı */
    public int signOutEverywhere(User user) {
        int closed = refreshTokens.revokeAll(user.getId());
        cutoff.cutOff(user.getEmail());
        // Bağlantılar işlem kaydedildikten sonra kapansın: şifre değişikliği geri
        // alınırsa kimse boşuna koparılmasın.
        String email = user.getEmail();
        AfterCommit.run(() -> realtimeSessions.closeAllFor(email));
        return closed;
    }
}
