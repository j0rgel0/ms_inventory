package com.lox.inventoryservice.api.controllers;

import com.lox.inventoryservice.api.exceptions.InventoryNotFoundException;
import com.lox.inventoryservice.api.models.Inventory;
import com.lox.inventoryservice.api.models.page.InventoryPage;
import com.lox.inventoryservice.api.models.responses.InventoryResponse;
import com.lox.inventoryservice.api.services.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    public Mono<ResponseEntity<InventoryResponse>> createInventory(
            @Valid @RequestBody Inventory inventory) {
        return inventoryService.createInventory(inventory)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error in createInventory: {}", e.getMessage());
                    if (e instanceof IllegalArgumentException) {
                        return Mono.just(ResponseEntity.badRequest().body(null));
                    }
                    return Mono.just(ResponseEntity.status(500).body(null));
                });
    }

    @GetMapping("/{productId}")
    public Mono<ResponseEntity<InventoryResponse>> getInventoryByProductId(
            @PathVariable("productId") UUID productId) {
        return inventoryService.getInventoryByProductId(productId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof InventoryNotFoundException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    log.error("Error in getInventoryByProductId: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(500).build());
                });
    }

    @PutMapping("/{productId}")
    public Mono<ResponseEntity<InventoryResponse>> updateInventory(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody Inventory inventory) {
        return inventoryService.updateInventory(productId, inventory)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof InventoryNotFoundException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    log.error("Error in updateInventory: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(500).body(null));
                });
    }

    @DeleteMapping("/{productId}")
    public Mono<ResponseEntity<Void>> deleteInventory(@PathVariable("productId") UUID productId) {
        return inventoryService.deleteInventory(productId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> {
                    if (e instanceof InventoryNotFoundException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    log.error("Error in deleteInventory: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(500).build());
                });
    }

    @GetMapping
    public Mono<ResponseEntity<InventoryPage>> listInventory(
            @RequestParam(required = false) List<String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {

        // Validate parameters
        if (page < 0) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        if (size <= 0) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        // Build Pageable
        Pageable pageable;
        if (sort != null && !sort.isEmpty()) {
            String[] sortParams = sort.split(",");
            if (sortParams.length == 2) {
                pageable = PageRequest.of(page, size,
                        Sort.by(Sort.Direction.fromString(sortParams[1]), sortParams[0]));
            } else {
                pageable = PageRequest.of(page, size);
            }
        } else {
            pageable = PageRequest.of(page, size);
        }

        return inventoryService.listInventory(filters, pageable)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Error in listInventory: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(500).build());
                });
    }

    @PostMapping("/reserve")
    public Mono<ResponseEntity<InventoryResponse>> reserveInventory(
            @RequestParam UUID productId,
            @RequestParam Integer quantity) {
        return inventoryService.reserveInventory(productId, quantity)
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("Error in reserveInventory: ", e));
    }

    @PostMapping("/release")
    public Mono<ResponseEntity<InventoryResponse>> releaseInventory(
            @RequestParam UUID productId,
            @RequestParam Integer quantity) {
        return inventoryService.releaseInventory(productId, quantity)
                .map(ResponseEntity::ok)
                .doOnError(e -> log.error("Error in releaseInventory: ", e));
    }

}
