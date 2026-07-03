package com.flowsight.exception;

import com.flowsight.dto.simulation.ScenarioRequest;
import com.flowsight.dto.simulation.ScenarioType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Verifies the validation / bad-input exception handlers map to HTTP 400
class GlobalExceptionHandlerValidationTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (factory != null) factory.close();
    }

    @Test
    void constraintViolation_mapsTo400_withFieldViolations() {
        ScenarioRequest req = new ScenarioRequest();
        req.setType(ScenarioType.LOAN_EMI);
        req.setAmount(new BigDecimal("1000"));
        req.setTenureMonths(5000); // exceeds @Max(600)

        Set<ConstraintViolation<ScenarioRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();

        ResponseEntity<ApiError> resp =
            handler.handleConstraintViolation(new ConstraintViolationException(violations));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getViolations()).anyMatch(v -> v.contains("tenureMonths"));
    }

    @Test
    void scenarioAmount_overMagnitudeCap_isRejected() {
        ScenarioRequest req = new ScenarioRequest();
        req.setType(ScenarioType.ONE_TIME_PURCHASE);
        req.setAmount(new BigDecimal("9999999999999")); // 13 integer digits, over cap + @Digits

        Set<ConstraintViolation<ScenarioRequest>> violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("amount"));
    }

    @Test
    void scenarioNegativeAmount_withinRange_isAllowed() {
        // SAVINGS_ADJUSTMENT permits a signed (negative) amount.
        ScenarioRequest req = new ScenarioRequest();
        req.setType(ScenarioType.SAVINGS_ADJUSTMENT);
        req.setAmount(new BigDecimal("-5000.00"));

        Set<ConstraintViolation<ScenarioRequest>> violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    @Test
    void typeMismatch_mapsTo400_namingTheParameter() throws Exception {
        Method m = Target.class.getDeclaredMethod("byId", UUID.class);
        MethodParameter param = new MethodParameter(m, 0);
        var ex = new MethodArgumentTypeMismatchException(
            "not-a-uuid", UUID.class, "id", param, new IllegalArgumentException("bad"));

        ResponseEntity<ApiError> resp = handler.handleTypeMismatch(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).contains("id").contains("UUID");
    }

    @Test
    void missingParam_mapsTo400() {
        var ex = new MissingServletRequestParameterException("months", "int");
        ResponseEntity<ApiError> resp = handler.handleMissingParam(ex);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().getMessage()).contains("months");
    }

    @Test
    void unreadableBody_mapsTo400_withoutEchoingContent() {
        var ex = new HttpMessageNotReadableException("secret payload detail", (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<ApiError> resp = handler.handleUnreadable(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getMessage()).doesNotContain("secret payload detail");
    }

    // Reflection target for building a MethodParameter.
    @SuppressWarnings("unused")
    private static final class Target {
        void byId(UUID id) { }
    }
}
