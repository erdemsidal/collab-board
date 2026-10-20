package com.collabboard.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Configuration
public class JacksonConfig {

    /**
     * Jackson ObjectMapper — Java 8 date/time desteği.
     * Spring Boot auto-config bunu zaten yapar ama explicit tanım
     * custom serializer/deserializer eklemeyi kolaylaştırır.
     *
     * REST yanıtları da canlı (Redis → WebSocket) mesajlar da bu nesneyle
     * yazılır; zaman biçimi her yerde aynı olur.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(instantTimes());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }

    /**
     * LocalDateTime'ı saat dilimi işaretiyle yazar: "2026-10-14T16:19:22Z".
     *
     * Kayıtlar sunucunun saatiyle tutulur (canlıda UTC). İşaretsiz yazılan
     * "2026-10-14T16:19:22" değerini tarayıcı KENDİ saati sanar; Türkiye'deki
     * bir kullanıcı az önce olan her şeyi "3 sa önce" görürdü. İşaretli değer
     * her tarayıcıda doğru ana karşılık gelir.
     */
    private static SimpleModule instantTimes() {
        SimpleModule module = new SimpleModule("InstantLocalDateTime");
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeString(value.atZone(ZoneId.systemDefault()).toInstant().toString());
            }
        });
        // Okurken de aynı kural: işaretliyse sunucu saatine çevir, değilse olduğu gibi al.
        module.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getValueAsString().trim();
                boolean hasZone = text.endsWith("Z") || text.matches(".*[+-]\\d{2}:?\\d{2}$");
                return hasZone
                        ? OffsetDateTime.parse(text).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                        : LocalDateTime.parse(text);
            }
        });
        return module;
    }
}
