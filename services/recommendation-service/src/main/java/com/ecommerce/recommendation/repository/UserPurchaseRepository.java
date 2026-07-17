package com.ecommerce.recommendation.repository;

import com.ecommerce.recommendation.model.UserPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserPurchaseRepository extends JpaRepository<UserPurchase, Long> {

    @Query("SELECT DISTINCT up.productId FROM UserPurchase up WHERE up.userId = :userId")
    List<String> findDistinctProductIdsByUserId(String userId);
}
