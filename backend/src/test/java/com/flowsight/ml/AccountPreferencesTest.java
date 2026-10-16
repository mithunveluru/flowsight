package com.flowsight.ml;

import com.flowsight.dto.account.AccountResponse;
import com.flowsight.dto.account.UpdatePreferencesRequest;
import com.flowsight.entity.Role;
import com.flowsight.entity.User;
import com.flowsight.service.AccountService;
import com.flowsight.service.AuditLogService;
import com.flowsight.service.ReceiptQuotaService;
import com.flowsight.service.UserService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The preference endpoint takes no user id: it can only ever change the caller's own flag.
@ExtendWith(MockitoExtension.class)
class AccountPreferencesTest {

    @Mock private UserService            userService;
    @Mock private ReceiptQuotaService    quotaService;
    @Mock private AuditLogService        auditLogService;
    @Mock private PersonalizationService personalization;
    @InjectMocks private AccountService  accountService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email("me@example.com").passwordHash("x").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    }

    @Test
    void accountReportsThePersonalizationFlag() {
        when(userService.findById(user.getId())).thenReturn(user);
        when(personalization.isEnabled(user.getId())).thenReturn(true);

        assertThat(accountService.getAccount(user.getId()).isPersonalizationEnabled()).isTrue();
    }

    @Test
    void updateChangesOnlyTheCallersFlagAndIsAudited() {
        when(userService.findById(user.getId())).thenReturn(user);
        when(personalization.isEnabled(user.getId())).thenReturn(false);
        UpdatePreferencesRequest request = new UpdatePreferencesRequest();
        request.setPersonalizationEnabled(false);

        AccountResponse response = accountService.updatePreferences(user, request);

        assertThat(response.isPersonalizationEnabled()).isFalse();
        verify(personalization).setEnabled(user.getId(), false);
        verify(personalization, times(1)).setEnabled(any(), anyBoolean());
        verify(auditLogService).log(eq(user), eq("PREFERENCES_UPDATED"), eq("User"),
            eq(user.getId().toString()), eq(Map.of("personalizationEnabled", false)));
    }

    @Test
    void requestRequiresAnExplicitValue() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new UpdatePreferencesRequest()))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("personalizationEnabled");
        }
    }
}
