package com.freightauction.auction.controller;

import com.freightauction.auction.dto.AuctionResponse;
import com.freightauction.auction.dto.CreateAuctionRequest;
import com.freightauction.auction.service.AuctionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/auctions")
@Tag(name = "Auctions", description = "Auctions Endpoints")
public class AuctionController {
    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }


    @PostMapping
    @Operation(summary = "Create new Auction")
    @ApiResponse(responseCode = "201", description = "Auction Created")
    @ApiResponse(responseCode = "400", description = "Error")
    public ResponseEntity<AuctionResponse> create(
            @Valid @RequestBody CreateAuctionRequest request,
            @RequestAttribute("authenticatedUserId") UUID createdByUserId
    ) {
        log.info("Request received: POST /v1/auctions, loadId={}, createdByUserId={}", request.loadId(), createdByUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionService.create(request, createdByUserId));
    }

    @GetMapping
    @Operation(summary = "Get all Auctions")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public ResponseEntity<List<AuctionResponse>> findAll() {
        log.info("Request received: GET /v1/auctions");
        return ResponseEntity.ok(auctionService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Auctions by ID")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public ResponseEntity<AuctionResponse> findById(@PathVariable UUID id) {
        log.info("Request received: GET /v1/auctions/{id}, auctionId={}", id);
        return ResponseEntity.ok(auctionService.findById(id));
    }

    @PatchMapping("/{id}/close")
    @Operation(summary = "Close Auction")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public ResponseEntity<AuctionResponse> close(
            @PathVariable UUID id,
            @RequestAttribute("authenticatedUserId") UUID userId
    ) {
        log.info("Request received: PATCH /v1/auctions/{id}/close, auctionId={}, userId={}", id, userId);
        return ResponseEntity.ok(auctionService.close(id));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(IllegalStateException.class)
    public Map<String, String> handleIllegalState(IllegalStateException ex) {
        return Map.of("error", ex.getMessage());
    }
}
