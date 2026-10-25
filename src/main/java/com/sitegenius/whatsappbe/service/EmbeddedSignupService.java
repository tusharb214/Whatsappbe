
package com.sitegenius.whatsappbe.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddedSignupService {

    private final RestClient restClient;

    @Value("${whatsapp.meta.app-id}")
    private String appId;

    @Value("${whatsapp.meta.app-secret}")
    private String appSecret;

    @Value("${whatsapp.meta.graph-api-url:https://graph.facebook.com}")
    private String graphApiUrl;

    @Value("${whatsapp.meta.graph-api-version:v23.0}")
    private String graphApiVersion;

    public EmbeddedSignupService() {
        this.restClient = RestClient.builder().build();
    }

    public String exchangeCodeForAccessToken(String code) {

        String url = UriComponentsBuilder
                .fromUriString(graphApiUrl)
                .pathSegment(graphApiVersion, "oauth", "access_token")
                .queryParam("client_id", appId)
                .queryParam("client_secret", appSecret)
                .queryParam("code", code)
                .build()
                .toUriString();

        Map<?, ?> response = restClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException(
                    "Meta did not return an access token."
            );
        }

        return response.get("access_token").toString();
    }

    public Map<?, ?> getPhoneDetails(
            String phoneNumberId,
            String accessToken
    ) {
        String url = UriComponentsBuilder
                .fromUriString(graphApiUrl)
                .pathSegment(graphApiVersion, phoneNumberId)
                .queryParam("fields", "display_phone_number,verified_name")
                .build()
                .toUriString();

        return restClient.get()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }

    public boolean wabaContainsPhone(
            String wabaId,
            String phoneNumberId,
            String accessToken
    ) {
        String url = UriComponentsBuilder
                .fromUriString(graphApiUrl)
                .pathSegment(graphApiVersion, wabaId, "phone_numbers")
                .queryParam("fields", "id")
                .build()
                .toUriString();

        Map<?, ?> response = restClient.get()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);

        if (response == null
                || !(response.get("data") instanceof List<?> data)) {
            return false;
        }

        return data.stream().anyMatch(item ->
                item instanceof Map<?, ?> map
                        && phoneNumberId.equals(
                        String.valueOf(map.get("id"))
                )
        );
    }
}
