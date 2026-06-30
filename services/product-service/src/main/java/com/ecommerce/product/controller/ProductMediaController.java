package com.ecommerce.product.controller;

import com.ecommerce.product.service.ProductMediaService;
import com.ecommerce.product.service.ProductMediaService.MediaItem;
import com.ecommerce.product.service.ProductMediaService.PresignedUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Product media API (v0.0.13). Through the gateway: {@code POST /api/products/{id}/media/presign-upload}
 * (authenticated — non-GET) returns a presigned PUT URL; {@code GET /api/products/{id}/media} (public)
 * lists presigned GET URLs.
 */
@RestController
@RequestMapping("/products/{productId}/media")
@RequiredArgsConstructor
public class ProductMediaController {

    private final ProductMediaService mediaService;

    @PostMapping("/presign-upload")
    public PresignedUpload presignUpload(@PathVariable String productId,
                                         @RequestParam String filename,
                                         @RequestParam(required = false) String contentType) {
        return mediaService.presignUpload(productId, filename, contentType);
    }

    @GetMapping
    public List<MediaItem> list(@PathVariable String productId) {
        return mediaService.list(productId);
    }
}
