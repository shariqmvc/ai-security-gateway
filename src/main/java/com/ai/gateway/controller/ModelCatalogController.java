package com.ai.gateway.controller;

import com.ai.gateway.common.APIResponse;
import com.ai.gateway.routing.registry.ModelCatalogItem;
import com.ai.gateway.routing.registry.ModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelCatalogController {

    private final ModelRegistry modelRegistry;

    @GetMapping
    public ResponseEntity<APIResponse<List<ModelCatalogItem>>> models() {
        List<ModelCatalogItem> items = modelRegistry.findAll().stream()
                .map(ModelCatalogItem::from)
                .toList();
        return ResponseEntity.ok(APIResponse.<List<ModelCatalogItem>>builder()
                .success(true)
                .message("Model catalog returned successfully.")
                .data(items)
                .build());
    }
}
