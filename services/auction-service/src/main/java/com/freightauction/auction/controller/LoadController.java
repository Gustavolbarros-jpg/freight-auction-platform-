package com.freightauction.auction.controller;

import com.freightauction.auction.dto.CreateLoadRequest;
import com.freightauction.auction.dto.LoadResponse;
import com.freightauction.auction.service.LoadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/loads")
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
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(loadService.create(request, createdByUserId));
    }

    @GetMapping
    public ResponseEntity<List<LoadResponse>> findAll() {
        return ResponseEntity.ok(loadService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoadResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(loadService.findById(id)); // 200 OK com a load
    }

    @PutMapping("/{id}")
    public ResponseEntity<LoadResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateLoadRequest request
    ) {
        return ResponseEntity.ok(loadService.update(id, request)); // 200 OK com a load atualizada
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        loadService.delete(id);
        return ResponseEntity.noContent().build(); // 204 No Content — deletou, sem body
    }
}