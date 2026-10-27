package com.collabboard.chathook.dto;

import com.collabboard.chathook.ChatProvider;

/**
 * Kaydetmeden önce "Deneme mesajı gönder": formdaki adres denenir, hiçbir şey saklanmaz.
 * locale verilmezse isteğin dili.
 */
public record ChatHookTestRequest(
        ChatProvider provider,
        String url,
        String locale
) {
}
