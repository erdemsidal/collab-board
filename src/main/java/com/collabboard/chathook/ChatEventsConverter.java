package com.collabboard.chathook;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seçili olaylar veritabanında virgülle ayrılmış tek bir metin: "CARD_CREATED,CARD_DONE".
 *
 * Ayrı bir tablo ya da dizi sütunu gerekmiyor: liste en fazla dört öğe, yalnızca
 * bütün hâliyle okunuyor ve yazılıyor, içinde arama yapılmıyor. Tanınmayan bir ad
 * (kodu geri alınmış bir olay türü) okurken sessizce düşülür, bağlantıyı bozmaz.
 */
@Converter
public class ChatEventsConverter implements AttributeConverter<Set<ChatEvent>, String> {

    @Override
    public String convertToDatabaseColumn(Set<ChatEvent> events) {
        if (events == null || events.isEmpty()) {
            return "";
        }
        // EnumSet sırası: her kayıtta aynı sıra, karşılaştırması kolay.
        return EnumSet.copyOf(events).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<ChatEvent> convertToEntityAttribute(String value) {
        Set<ChatEvent> out = EnumSet.noneOf(ChatEvent.class);
        if (value == null || value.isBlank()) {
            return out;
        }
        Arrays.stream(value.split(","))
                .map(String::trim)
                .forEach(name -> Arrays.stream(ChatEvent.values())
                        .filter(e -> e.name().equals(name))
                        .findFirst()
                        .ifPresent(out::add));
        return out;
    }
}
