package com.flowsight.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMerchantRuleRepository extends JpaRepository<UserMerchantRule, UUID> {

    List<UserMerchantRule> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<UserMerchantRule> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserMerchantRule> findByUserIdAndMatchKey(UUID userId, String matchKey);
}
