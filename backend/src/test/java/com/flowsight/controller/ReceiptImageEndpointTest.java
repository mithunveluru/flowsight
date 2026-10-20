package com.flowsight.controller;

import com.flowsight.entity.Role;
import com.flowsight.entity.User;
import com.flowsight.exception.GlobalExceptionHandler;
import com.flowsight.service.ReceiptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReceiptImageEndpointTest {

    private final ReceiptService receiptService = mock(ReceiptService.class);
    private final User user = User.builder().id(UUID.randomUUID()).email("img@example.com").passwordHash("x").role(Role.USER).build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ReceiptController(receiptService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void servesTheImageForTheSignedInUser_neverCached() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
        when(receiptService.findImage(id, user.getId())).thenReturn(Optional.of(jpeg));

        mvc.perform(get("/api/v1/receipts/{id}/image", id))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/jpeg"))
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(content().bytes(jpeg));
    }

    @Test
    void goneOrForeignImage_is404() throws Exception {
        UUID id = UUID.randomUUID();
        when(receiptService.findImage(id, user.getId())).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/receipts/{id}/image", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/receipts/{id}/image", id).header("Accept", "image/*")).andExpect(status().isNotFound());
    }

    @Test
    void nonUuidId_isRejectedBeforeAnyLookup() throws Exception {
        mvc.perform(get("/api/v1/receipts/{id}/image", "..%2F..%2Fetc%2Fpasswd")).andExpect(status().isBadRequest());
    }
}
