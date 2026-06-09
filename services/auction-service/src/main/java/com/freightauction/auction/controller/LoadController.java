package com.freightauction.auction.controller;

import com.freightauction.auction.dto.CreateLoadRequest;
import com.freightauction.auction.dto.LoadResponse;
import com.freightauction.auction.service.LoadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/loads")
public class LoadController {

    private final LoadService loadService;

    public LoadController(LoadService loadService) {
        this.loadService = loadService;
    }

    @PostMapping
    public ResponseEntity<LoadResponse> create(
            @Valid @RequestBody CreateLoadRequest request,
            @RequestHeader("X-User-Id") UUID createdByUserId
    ) {
        LoadResponse response = loadService.create(request, createdByUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}