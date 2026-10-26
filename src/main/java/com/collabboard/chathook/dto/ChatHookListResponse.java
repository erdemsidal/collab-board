package com.collabboard.chathook.dto;

import java.util.List;

/** Panonun kanal bağlantıları ve bir panoya en fazla kaç tane bağlanabileceği. */
public record ChatHookListResponse(
        int maxHooks,
        List<ChatHookResponse> hooks
) {
}
