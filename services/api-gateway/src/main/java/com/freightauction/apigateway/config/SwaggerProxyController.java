package com.freightauction.apigateway.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api-docs")
public class SwaggerProxyController {

    private static final Logger log = LoggerFactory.getLogger(SwaggerProxyController.class);

    @Value("${swagger.services.auth:http://auth-service:8084}")
    private String authServiceUrl;

    @Value("${swagger.services.auction:http://auction-service:8081}")
    private String auctionServiceUrl;

    @Value("${swagger.services.bid:http://bid-service:8082}")
    private String bidServiceUrl;

    @PostConstruct
    void logUrls() {
        log.info("SwaggerProxyController iniciado. Auth={}, Auction={}, Bid={}",
                authServiceUrl, auctionServiceUrl, bidServiceUrl);
    }

    @GetMapping("/auth")
    public ResponseEntity<String> authDocs() {
        return fetchDocs(authServiceUrl + "/api-docs");
    }

    @GetMapping("/auction")
    public ResponseEntity<String> auctionDocs() {
        return fetchDocs(auctionServiceUrl + "/api-docs");
    }

    @GetMapping("/bid")
    public ResponseEntity<String> bidDocs() {
        return fetchDocs(bidServiceUrl + "/api-docs");
    }

    private ResponseEntity<String> fetchDocs(String targetUrl) {
        log.info("Buscando api-docs em: {}", targetUrl);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(targetUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(10_000);
            conn.setRequestProperty("Accept", "application/json");
            conn.connect();

            int status = conn.getResponseCode();
            log.info("Resposta de {}: HTTP {}", targetUrl, status);

            InputStream stream = (status < 400) ? conn.getInputStream() : conn.getErrorStream();
            String body = stream != null
                    ? new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    : "";

            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);

        } catch (IOException e) {
            log.error("Falha ao buscar {}: {}", targetUrl, e.getMessage(), e);
            return ResponseEntity.status(503)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Serviço indisponível\",\"detail\":\"" + e.getMessage() + "\"}");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}