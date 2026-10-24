package com.collabboard.auth.dto;

import java.util.List;

/** Kurulu dış hesap sağlayıcıları: {"providers":[{"id":"google","name":"Google"}]}. */
public record ProvidersResponse(List<Provider> providers) {

    /**
     * @param id   akışı başlatan adresin son parçası: /oauth2/authorization/{id}
     * @param name düğmede görünen ad
     */
    public record Provider(String id, String name) {
    }
}
