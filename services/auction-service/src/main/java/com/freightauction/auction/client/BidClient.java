package com.freightauction.auction.client;

import com.freightauction.auction.dto.BestBidResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Component
public class BidClient {

    private final RestClient restClient;

    public BidClient(@Value("${bid.service.url:http://localhost:8082}") String bidServiceUrl) {
        this.restClient = RestClient.create(bidServiceUrl);
    }

    public Optional<BestBidResponse> findBestBid(UUID auctionId) {
        try {
            BestBidResponse response = restClient.get()
                    .uri("/bids/auctions/{auctionId}/best", auctionId)
                    .retrieve()
                    .body(BestBidResponse.class);
            return Optional.ofNullable(response);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        }
    }
}
