package com.ecommerce.cart.client;

import com.ecommerce.grpc.product.CheckExistsRequest;
import com.ecommerce.grpc.product.ProductServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductServiceClient {

    @GrpcClient("product-service")
    private ProductServiceGrpc.ProductServiceBlockingStub stub;

    public boolean exists(String productId) {
        try {
            return stub.checkExists(
                CheckExistsRequest.newBuilder().setProductId(productId).build()
            ).getExists();
        } catch (Exception e) {
            log.warn("[PRODUCT-GRPC] checkExists failed for productId={}: {}", productId, e.getMessage());
            return false;
        }
    }
}
