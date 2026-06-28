package com.freightauction.auction.controller;

import com.freightauction.auction.dto.CreateLoadRequest;
import com.freightauction.auction.dto.LoadResponse;
import com.freightauction.auction.service.LoadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/loads")
@Tag(name = "Loads", description = "Endpoints of Loads")
public class LoadController {

    private final LoadService loadService;

    public LoadController(LoadService loadService) {
        this.loadService = loadService;
    }

    @PostMapping
    @Operation(summary = "Create Load")
    @ApiResponse(responseCode = "201", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public ResponseEntity<LoadResponse> create(
            @Valid @RequestBody CreateLoadRequest request,
            @RequestAttribute("authenticatedUserId") UUID createdByUserId
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(loadService.create(request, createdByUserId));
    }

    @GetMapping
    @Operation(summary = "Get all Loads")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public ResponseEntity<List<LoadResponse>> findAll() {
        return ResponseEntity.ok(loadService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Load by id")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public ResponseEntity<LoadResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(loadService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update by id")
    @ApiResponse(responseCode = "200", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public ResponseEntity<LoadResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateLoadRequest request
    ) {
        return ResponseEntity.ok(loadService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Load")
    @ApiResponse(responseCode = "204", description = "Success")
    @ApiResponse(responseCode = "400", description = "Error")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        loadService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
