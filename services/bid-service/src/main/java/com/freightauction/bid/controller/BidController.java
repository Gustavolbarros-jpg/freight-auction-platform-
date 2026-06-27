package com.freightauction.bid.controller;

import com.freightauction.bid.dto.BestBidResponse;
import com.freightauction.bid.dto.BidAcceptedResponse;
import com.freightauction.bid.dto.BidResponse;
import com.freightauction.bid.dto.CreateBidRequest;
import com.freightauction.bid.service.BestBidService;
import com.freightauction.bid.service.BidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/bids")
@Tag(name = "Bids", description = "Endpoints Bids")
public class BidController {

    private final BidService bidService;
    private final BestBidService bestBidService;

    public BidController(BidService bidService, BestBidService bestBidService) {
        this.bidService = bidService;
        this.bestBidService = bestBidService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Create Bid")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public BidAcceptedResponse create(
            @Valid @RequestBody CreateBidRequest request,
            @RequestAttribute("authenticatedUserId") UUID carrierId
    ) {
        log.info("Request received: POST /bids, auctionId={}, carrierId={}, amount={}",
                request.auctionId(), carrierId, request.amount());
        return bidService.placeBid(request, carrierId);
    }

    @GetMapping("/auctions/{auctionId}/best")
    @Operation(summary = "Find best bid")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public BestBidResponse findBestBid(@PathVariable UUID auctionId) {
        log.info("Request received: GET /bids/auctions/{auctionId}/best, auctionId={}", auctionId);
        return bestBidService.findBestBid(auctionId);
    }

    @GetMapping("/auctions/{auctionId}")
    @Operation(summary = "List bids by auction")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public List<BidResponse> findByAuctionId(@PathVariable UUID auctionId) {
        log.info("Request received: GET /bids/auctions/{auctionId}, auctionId={}", auctionId);
        return bidService.findByAuctionId(auctionId);
    }
}
