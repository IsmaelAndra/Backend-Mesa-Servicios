package com.mesaayuda.service;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/** Factoría interna para el RestTemplate usado contra Microsoft Graph. */
final class GraphHttpClient {

    private GraphHttpClient() {
    }

    static RestTemplate create() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
        return new RestTemplate(factory);
    }
}
