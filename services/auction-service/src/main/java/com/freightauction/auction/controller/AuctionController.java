package com.freightauction.auction.controller;

import com.freightauction.auction.dto.AuctionResponse;
import com.freightauction.auction.dto.CreateAuctionRequest;
import com.freightauction.auction.service.AuctionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
            @RequestHeader("X-User-Id") UUID createdByUserId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionService.create(request, createdByUserId));
    }

    @GetMapping
    public ResponseEntity<List<AuctionResponse>> findAll() {
        return ResponseEntity.ok(auctionService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(auctionService.findById(id));
    }

    @PatchMapping("/{id}/close")        // PATCH porque é uma mudança parcial de estado
    public ResponseEntity<AuctionResponse> close(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(auctionService.close(id));
    }
}
