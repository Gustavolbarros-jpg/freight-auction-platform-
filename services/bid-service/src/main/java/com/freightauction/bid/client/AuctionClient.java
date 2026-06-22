package com.freightauction.bid.client;

import com.freightauction.bid.dto.AuctionSummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class AuctionClient {

    private final RestClient restClient;

    public AuctionClient(@Value("${auction.service.url:http://localhost:8081}") String auctionServiceUrl) {
        this.restClient = RestClient.create(auctionServiceUrl);
    }

    public AuctionSummaryResponse findById(UUID auctionId) {
        try {
            return restClient.get()
                    .uri("/v1/auctions/{id}", auctionId)
                    .retrieve()
                    .body(AuctionSummaryResponse.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found", exception);
        }
    }
}
