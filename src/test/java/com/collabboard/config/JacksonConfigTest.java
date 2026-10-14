package com.collabboard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Zamanlar JSON'da saat dilimi işaretiyle gider; tarayıcı nerede olursa olsun doğru anı görür. */
class JacksonConfigTest {

    private final ObjectMapper mapper = new JacksonConfig().objectMapper();

    @Test
    @DisplayName("LocalDateTime 'Z' ile, aynı ana karşılık gelen değer olarak yazılır")
    void zamanDilimiIsaretliYazilir() throws Exception {
        LocalDateTime at = LocalDateTime.of(2026, 10, 14, 16, 19, 22);

        String json = mapper.writeValueAsString(Map.of("createdAt", at));
        String written = mapper.readTree(json).get("createdAt").asText();

        assertThat(written).endsWith("Z");
        assertThat(Instant.parse(written)).isEqualTo(at.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("Yazılan değer okununca aynı yerel zamana döner; işaretsiz değer de okunur")
    void gidipGelir() throws Exception {
        LocalDateTime at = LocalDateTime.of(2026, 3, 29, 2, 30, 5, 123_000_000).plusHours(12);

        String json = mapper.writeValueAsString(at);

        assertThat(mapper.readValue(json, LocalDateTime.class)).isEqualTo(at);
        assertThat(mapper.readValue("\"2026-10-14T16:19:22\"", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 10, 14, 16, 19, 22));
    }
}
