package com.collabboard.invitation;

/** Bir davetin yaşam döngüsü. Yalnızca PENDING durumundaki davete yanıt verilebilir. */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    /** Gönderen, karşı taraf yanıtlamadan geri çekti. */
    CANCELLED
}
