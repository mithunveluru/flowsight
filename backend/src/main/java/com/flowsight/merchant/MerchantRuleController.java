package com.flowsight.merchant;

import com.flowsight.dto.merchant.ApplyPreviewResponse;
import com.flowsight.dto.merchant.ApplyResultResponse;
import com.flowsight.dto.merchant.MerchantRuleResponse;
import com.flowsight.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchant-rules")
@RequiredArgsConstructor
public class MerchantRuleController {

    private final MerchantRuleService ruleService;

    @GetMapping
    public ResponseEntity<List<MerchantRuleResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ruleService.list(user.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        ruleService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    // how many of the user's other transactions the rule would change, with a sample
    @PostMapping("/{id}/preview")
    public ResponseEntity<ApplyPreviewResponse> preview(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ruleService.preview(id, user));
    }

    @PostMapping("/{id}/apply")
    public ResponseEntity<ApplyResultResponse> apply(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ruleService.apply(id, user));
    }
}
