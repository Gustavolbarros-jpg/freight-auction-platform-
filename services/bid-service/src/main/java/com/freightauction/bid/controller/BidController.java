package com.freightauction.bid.controller;

import com.freightauction.bid.dto.BestBidResponse;
import com.freightauction.bid.dto.BidAcceptedResponse;
import com.freightauction.bid.dto.CreateBidRequest;
import com.freightauction.bid.service.BestBidService;
import com.freightauction.bid.service.BidService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("v1/bids")
public class BidController {

    private final BidService bidService;
    private final BestBidService bestBidService;

    public BidController(BidService bidService, BestBidService bestBidService) {
        this.bidService = bidService;
        this.bestBidService = bestBidService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BidAcceptedResponse create(@Valid @RequestBody CreateBidRequest request) {
        return bidService.placeBid(request);
    }

    @GetMapping("/auctions/{auctionId}/best")
    public BestBidResponse findBestBid(@PathVariable UUID auctionId) {
        return bestBidService.findBestBid(auctionId);
    }
}
