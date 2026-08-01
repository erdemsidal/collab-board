package com.collabboard.board.operation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * İstemciden gelen bir kart operasyonu (ADR 0001: operasyon tabanlı model).
 *
 * Gelen JSON'daki "type" alanına göre doğru alt tipe ayrıştırılır.
 *
 * sealed olması bilinçli: operasyonu işleyen switch'te tiplerden biri eksik
 * kalırsa kod derlenmez — yeni bir operasyon eklendiğinde onu ele almayı unutmak
 * derleme hatasına dönüşür.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
                @JsonSubTypes.Type(value = AddCardOp.class, name = "ADD_CARD"),
                @JsonSubTypes.Type(value = MoveCardOp.class, name = "MOVE_CARD"),
                @JsonSubTypes.Type(value = EditCardOp.class, name = "EDIT_CARD"),
                @JsonSubTypes.Type(value = DeleteCardOp.class, name = "DELETE_CARD"),
                @JsonSubTypes.Type(value = MoveColumnOp.class, name = "MOVE_COLUMN")
})
public sealed interface BoardOperation
        permits AddCardOp, MoveCardOp, EditCardOp, DeleteCardOp, MoveColumnOp {
}
