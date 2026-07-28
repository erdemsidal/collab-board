package com.collabboard.board.operation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * İstemciden gelen bir kart operasyonu (ADR 0001: operasyon tabanlı model).
 *
 * POLİMORFİK JSON — Jackson gelen JSON'daki "type" alanına bakıp doğru alt tipe
 * çevirir:
 * {"type":"ADD_CARD", "columnId":1, "title":"..."} → AddCardOp
 * {"type":"MOVE_CARD", "cardId":7, "toColumnId":2, ...} → MoveCardOp
 *
 * @JsonTypeInfo(property = "type"): ayrıştırmada hangi alanın "tip" olduğunu
 *                        söyler.
 * @JsonSubTypes: hangi tip adının hangi sınıfa karşılık geldiğini eşler.
 *
 *                SEALED (mühürlü): bu arayüzü SADECE aşağıdaki 'permits'
 *                listesindeki sınıflar
 *                uygulayabilir. Faydası: operasyonu işlerken switch yazınca
 *                derleyici tüm tipleri
 *                ele aldığımızı garanti eder. Yeni tip (EDIT/DELETE) eklenince,
 *                ele almayı
 *                unutursak kod DERLENMEZ. Yani kapsam büyüyünce güvenlik ağı
 *                compiler'da.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
                @JsonSubTypes.Type(value = AddCardOp.class, name = "ADD_CARD"),
                @JsonSubTypes.Type(value = MoveCardOp.class, name = "MOVE_CARD"),
                @JsonSubTypes.Type(value = EditCardOp.class, name = "EDIT_CARD"),
                @JsonSubTypes.Type(value = DeleteCardOp.class, name = "DELETE_CARD")
})
public sealed interface CardOperation permits AddCardOp, MoveCardOp, EditCardOp, DeleteCardOp {
}
