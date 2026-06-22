package com.freightauction.auction.controller;

import com.freightauction.auction.dto.AuctionResponse;
import com.freightauction.auction.dto.CreateAuctionRequest;
import com.freightauction.auction.service.AuctionService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/auctions")
public class AuctionController {
    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @PostMapping
    public ResponseEntity<AuctionResponse> create(
            @Valid @RequestBody CreateAuctionRequest request,
            @RequestAttribute("authenticatedUserId") UUID createdByUserId
    ) {
        log.info("Request received: POST /v1/auctions, loadId={}, createdByUserId={}", request.loadId(), createdByUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionService.create(request, createdByUserId));
    }

    @GetMapping
    public ResponseEntity<List<AuctionResponse>> findAll() {
        log.info("Request received: GET /v1/auctions");
        return ResponseEntity.ok(auctionService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponse> findById(@PathVariable UUID id) {
        log.info("Request received: GET /v1/auctions/{id}, auctionId={}", id);
        return ResponseEntity.ok(auctionService.findById(id));
    }

    @PatchMapping("/{id}/close")        // PATCH porque é uma mudança parcial de estado
    public ResponseEntity<AuctionResponse> close(
            @PathVariable UUID id,
            @RequestAttribute("authenticatedUserId") UUID userId
    ) {
        log.info("Request received: PATCH /v1/auctions/{id}/close, auctionId={}, userId={}", id, userId);
        return ResponseEntity.ok(auctionService.close(id));
    }
}
