package com.flowsight.onboarding;

import com.flowsight.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

interface OnboardingUserRepository extends Repository<User, UUID> {

    // row lock serializes concurrent seed/remove calls for one user
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockById(UUID id);
}
