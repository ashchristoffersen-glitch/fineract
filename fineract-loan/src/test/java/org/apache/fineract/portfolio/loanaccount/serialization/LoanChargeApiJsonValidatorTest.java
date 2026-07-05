/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.loanaccount.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.monetary.exception.InvalidCurrencyException;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.charge.exception.LoanChargeCannotBeAddedException;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargeRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.apache.fineract.portfolio.loanproduct.exception.LinkedAccountRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanChargeApiJsonValidatorTest {

    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();

    @Mock
    private ChargeRepositoryWrapper chargeRepository;

    @Mock
    private LoanChargeRepository loanChargeRepository;

    private LoanChargeApiJsonValidator underTest;

    @BeforeEach
    void setUp() {
        underTest = new LoanChargeApiJsonValidator(fromJsonHelper, chargeRepository, loanChargeRepository);
    }

    // -----------------------------------------------------------------------
    // validateAddLoanCharge
    // -----------------------------------------------------------------------

    @Test
    void validateAddLoanCharge_validPayload_shouldNotThrow() {
        String json = """
                {"chargeId": 1, "amount": 100, "locale": "en", "dateFormat": "dd MMMM yyyy"}
                """;
        assertDoesNotThrow(() -> underTest.validateAddLoanCharge(json));
    }

    @Test
    void validateAddLoanCharge_validPayloadWithDueDate_shouldNotThrow() {
        String json = """
                {"chargeId": 1, "amount": 100, "dueDate": "15 January 2025", "locale": "en", "dateFormat": "dd MMMM yyyy"}
                """;
        assertDoesNotThrow(() -> underTest.validateAddLoanCharge(json));
    }

    @Test
    void validateAddLoanCharge_blankJson_shouldThrowInvalidJsonException() {
        assertThrows(InvalidJsonException.class, () -> underTest.validateAddLoanCharge(""));
    }

    @Test
    void validateAddLoanCharge_nullJson_shouldThrowInvalidJsonException() {
        assertThrows(InvalidJsonException.class, () -> underTest.validateAddLoanCharge(null));
    }

    @Test
    void validateAddLoanCharge_missingChargeId_shouldThrowValidationException() {
        String json = """
                {"amount": 100, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateAddLoanCharge(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("chargeId.cannot.be.blank"));
    }

    @Test
    void validateAddLoanCharge_chargeIdZero_shouldThrowValidationException() {
        String json = """
                {"chargeId": 0, "amount": 100, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateAddLoanCharge(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("chargeId.not.greater.than.zero"));
    }

    @Test
    void validateAddLoanCharge_negativeChargeId_shouldThrowValidationException() {
        String json = """
                {"chargeId": -1, "amount": 100, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateAddLoanCharge(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("chargeId.not.greater.than.zero"));
    }

    @Test
    void validateAddLoanCharge_missingAmount_shouldThrowValidationException() {
        String json = """
                {"chargeId": 1, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateAddLoanCharge(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("amount.cannot.be.blank"));
    }

    @Test
    void validateAddLoanCharge_zeroAmount_shouldThrowValidationException() {
        String json = """
                {"chargeId": 1, "amount": 0, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateAddLoanCharge(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("amount.not.greater.than.zero"));
    }

    @Test
    void validateAddLoanCharge_negativeAmount_shouldThrowValidationException() {
        String json = """
                {"chargeId": 1, "amount": -50, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateAddLoanCharge(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("amount.not.greater.than.zero"));
    }

    @Test
    void validateAddLoanCharge_unsupportedParameter_shouldThrowUnsupportedParameterException() {
        String json = """
                {"chargeId": 1, "amount": 100, "locale": "en", "bogusParam": "value"}
                """;
        UnsupportedParameterException ex = assertThrows(UnsupportedParameterException.class,
                () -> underTest.validateAddLoanCharge(json));
        assertThat(ex.getUnsupportedParameters()).contains("bogusParam");
    }

    @Test
    void validateAddLoanCharge_emptyJsonObject_shouldThrowValidationException() {
        String json = "{}";
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateAddLoanCharge(json));
        assertThat(ex.getErrors()).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // validateUpdateOfLoanCharge
    // -----------------------------------------------------------------------

    @Test
    void validateUpdateOfLoanCharge_validPayload_shouldNotThrow() {
        String json = """
                {"amount": 200, "locale": "en"}
                """;
        assertDoesNotThrow(() -> underTest.validateUpdateOfLoanCharge(json));
    }

    @Test
    void validateUpdateOfLoanCharge_validPayloadWithDueDate_shouldNotThrow() {
        String json = """
                {"amount": 200, "dueDate": "15 January 2025", "locale": "en", "dateFormat": "dd MMMM yyyy"}
                """;
        assertDoesNotThrow(() -> underTest.validateUpdateOfLoanCharge(json));
    }

    @Test
    void validateUpdateOfLoanCharge_blankJson_shouldThrowInvalidJsonException() {
        assertThrows(InvalidJsonException.class, () -> underTest.validateUpdateOfLoanCharge(""));
    }

    @Test
    void validateUpdateOfLoanCharge_missingAmount_shouldThrowValidationException() {
        String json = """
                {"locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateUpdateOfLoanCharge(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("amount.cannot.be.blank"));
    }

    @Test
    void validateUpdateOfLoanCharge_zeroAmount_shouldThrowValidationException() {
        String json = """
                {"amount": 0, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateUpdateOfLoanCharge(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("amount.not.greater.than.zero"));
    }

    @Test
    void validateUpdateOfLoanCharge_negativeAmount_shouldThrowValidationException() {
        String json = """
                {"amount": -10, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateUpdateOfLoanCharge(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("amount.not.greater.than.zero"));
    }

    @Test
    void validateUpdateOfLoanCharge_unsupportedParameter_shouldThrowUnsupportedParameterException() {
        String json = """
                {"amount": 200, "locale": "en", "unknown": 1}
                """;
        UnsupportedParameterException ex = assertThrows(UnsupportedParameterException.class,
                () -> underTest.validateUpdateOfLoanCharge(json));
        assertThat(ex.getUnsupportedParameters()).contains("unknown");
    }

    @Test
    void validateUpdateOfLoanCharge_emptyJsonObject_shouldThrowValidationException() {
        String json = "{}";
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateUpdateOfLoanCharge(json));
        assertThat(ex.getErrors()).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // validateChargePaymentTransaction
    // -----------------------------------------------------------------------

    @Test
    void validateChargePaymentTransaction_validPayloadWithChargeId_shouldNotThrow() {
        String json = """
                {"transactionDate": "15 January 2025", "chargeId": 1, "locale": "en", "dateFormat": "dd MMMM yyyy"}
                """;
        assertDoesNotThrow(() -> underTest.validateChargePaymentTransaction(json, true));
    }

    @Test
    void validateChargePaymentTransaction_validPayloadWithoutChargeId_shouldNotThrow() {
        String json = """
                {"transactionDate": "15 January 2025", "locale": "en", "dateFormat": "dd MMMM yyyy"}
                """;
        assertDoesNotThrow(() -> underTest.validateChargePaymentTransaction(json, false));
    }

    @Test
    void validateChargePaymentTransaction_blankJson_shouldThrowInvalidJsonException() {
        assertThrows(InvalidJsonException.class, () -> underTest.validateChargePaymentTransaction("", true));
    }

    @Test
    void validateChargePaymentTransaction_missingTransactionDate_shouldThrowValidationException() {
        String json = """
                {"chargeId": 1, "locale": "en", "dateFormat": "dd MMMM yyyy"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateChargePaymentTransaction(json, true));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("transactionDate.cannot.be.blank"));
    }

    @Test
    void validateChargePaymentTransaction_missingChargeIdWhenIncluded_shouldThrowValidationException() {
        String json = """
                {"transactionDate": "15 January 2025", "locale": "en", "dateFormat": "dd MMMM yyyy"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateChargePaymentTransaction(json, true));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("chargeId.cannot.be.blank"));
    }

    @Test
    void validateChargePaymentTransaction_chargeIdZeroWhenIncluded_shouldThrowValidationException() {
        String json = """
                {"transactionDate": "15 January 2025", "chargeId": 0, "locale": "en", "dateFormat": "dd MMMM yyyy"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateChargePaymentTransaction(json, true));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("chargeId.not.greater.than.zero"));
    }

    @Test
    void validateChargePaymentTransaction_chargeIdUnsupportedWhenNotIncluded_shouldThrowUnsupportedParameterException() {
        String json = """
                {"transactionDate": "15 January 2025", "chargeId": 1, "locale": "en", "dateFormat": "dd MMMM yyyy"}
                """;
        UnsupportedParameterException ex = assertThrows(UnsupportedParameterException.class,
                () -> underTest.validateChargePaymentTransaction(json, false));
        assertThat(ex.getUnsupportedParameters()).contains("chargeId");
    }

    @Test
    void validateChargePaymentTransaction_installmentNumberZero_shouldThrowValidationException() {
        String json = """
                {"transactionDate": "15 January 2025", "installmentNumber": 0, "locale": "en", "dateFormat": "dd MMMM yyyy"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateChargePaymentTransaction(json, false));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("installmentNumber.not.greater.than.zero"));
    }

    @Test
    void validateChargePaymentTransaction_validInstallmentNumber_shouldNotThrow() {
        String json = """
                {"transactionDate": "15 January 2025", "installmentNumber": 3, "locale": "en", "dateFormat": "dd MMMM yyyy"}
                """;
        assertDoesNotThrow(() -> underTest.validateChargePaymentTransaction(json, false));
    }

    @Test
    void validateChargePaymentTransaction_emptyJsonObject_shouldThrowValidationException() {
        String json = "{}";
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateChargePaymentTransaction(json, false));
        assertThat(ex.getErrors()).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // validateLoanChargeRefundTransaction
    // -----------------------------------------------------------------------

    @Test
    void validateLoanChargeRefundTransaction_validPayload_shouldNotThrow() {
        String json = """
                {"loanChargeId": 5, "locale": "en"}
                """;
        assertDoesNotThrow(() -> underTest.validateLoanChargeRefundTransaction(json));
    }

    @Test
    void validateLoanChargeRefundTransaction_validPayloadWithOptionalFields_shouldNotThrow() {
        String json = """
                {"loanChargeId": 5, "transactionAmount": 100, "installmentNumber": 2, "locale": "en"}
                """;
        assertDoesNotThrow(() -> underTest.validateLoanChargeRefundTransaction(json));
    }

    @Test
    void validateLoanChargeRefundTransaction_blankJson_shouldThrowInvalidJsonException() {
        assertThrows(InvalidJsonException.class, () -> underTest.validateLoanChargeRefundTransaction(""));
    }

    @Test
    void validateLoanChargeRefundTransaction_missingLoanChargeId_shouldThrowValidationException() {
        String json = """
                {"locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateLoanChargeRefundTransaction(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("loanChargeId.cannot.be.blank"));
    }

    @Test
    void validateLoanChargeRefundTransaction_loanChargeIdZero_shouldThrowValidationException() {
        String json = """
                {"loanChargeId": 0, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateLoanChargeRefundTransaction(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("loanChargeId.not.greater.than.zero"));
    }

    @Test
    void validateLoanChargeRefundTransaction_negativeTransactionAmount_shouldThrowValidationException() {
        String json = """
                {"loanChargeId": 5, "transactionAmount": -100, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateLoanChargeRefundTransaction(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("transactionAmount.not.greater.than.zero"));
    }

    @Test
    void validateLoanChargeRefundTransaction_zeroTransactionAmount_shouldThrowValidationException() {
        String json = """
                {"loanChargeId": 5, "transactionAmount": 0, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateLoanChargeRefundTransaction(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("transactionAmount.not.greater.than.zero"));
    }

    @Test
    void validateLoanChargeRefundTransaction_installmentNumberZero_shouldThrowValidationException() {
        String json = """
                {"loanChargeId": 5, "installmentNumber": 0, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateLoanChargeRefundTransaction(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("installmentNumber.not.greater.than.zero"));
    }

    @Test
    void validateLoanChargeRefundTransaction_unsupportedParameter_shouldThrowUnsupportedParameterException() {
        String json = """
                {"loanChargeId": 5, "locale": "en", "notAllowed": true}
                """;
        UnsupportedParameterException ex = assertThrows(UnsupportedParameterException.class,
                () -> underTest.validateLoanChargeRefundTransaction(json));
        assertThat(ex.getUnsupportedParameters()).contains("notAllowed");
    }

    @Test
    void validateLoanChargeRefundTransaction_emptyJsonObject_shouldThrowValidationException() {
        String json = "{}";
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateLoanChargeRefundTransaction(json));
        assertThat(ex.getErrors()).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // validateLoanChargeAdjustmentRequest
    // -----------------------------------------------------------------------

    @Test
    void validateLoanChargeAdjustmentRequest_validPayload_shouldNotThrow() {
        String json = """
                {"amount": 50, "locale": "en"}
                """;
        assertDoesNotThrow(() -> underTest.validateLoanChargeAdjustmentRequest(1L, 2L, json));
    }

    @Test
    void validateLoanChargeAdjustmentRequest_validPayloadNoAmount_shouldNotThrow() {
        String json = """
                {"locale": "en"}
                """;
        assertDoesNotThrow(() -> underTest.validateLoanChargeAdjustmentRequest(1L, 2L, json));
    }

    @Test
    void validateLoanChargeAdjustmentRequest_blankJson_shouldThrowInvalidJsonException() {
        assertThrows(InvalidJsonException.class, () -> underTest.validateLoanChargeAdjustmentRequest(1L, 2L, ""));
    }

    @Test
    void validateLoanChargeAdjustmentRequest_nullLoanId_shouldThrowValidationException() {
        String json = """
                {"amount": 50, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateLoanChargeAdjustmentRequest(null, 2L, json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("loanId.cannot.be.blank"));
    }

    @Test
    void validateLoanChargeAdjustmentRequest_nullLoanChargeId_shouldThrowValidationException() {
        String json = """
                {"amount": 50, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateLoanChargeAdjustmentRequest(1L, null, json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("loanChargeId.cannot.be.blank"));
    }

    @Test
    void validateLoanChargeAdjustmentRequest_zeroAmount_shouldThrowValidationException() {
        String json = """
                {"amount": 0, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateLoanChargeAdjustmentRequest(1L, 2L, json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("amount.not.greater.than.zero"));
    }

    @Test
    void validateLoanChargeAdjustmentRequest_negativeAmount_shouldThrowValidationException() {
        String json = """
                {"amount": -10, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateLoanChargeAdjustmentRequest(1L, 2L, json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("amount.not.greater.than.zero"));
    }

    @Test
    void validateLoanChargeAdjustmentRequest_noteTooLong_shouldThrowValidationException() {
        String longNote = "A".repeat(1001);
        String json = """
                {"note": "%s", "locale": "en"}
                """.formatted(longNote);
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateLoanChargeAdjustmentRequest(1L, 2L, json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("note.exceeds.max.length"));
    }

    @Test
    void validateLoanChargeAdjustmentRequest_noteExactly1000_shouldNotThrow() {
        String note = "A".repeat(1000);
        String json = """
                {"note": "%s", "locale": "en"}
                """.formatted(note);
        assertDoesNotThrow(() -> underTest.validateLoanChargeAdjustmentRequest(1L, 2L, json));
    }

    @Test
    void validateLoanChargeAdjustmentRequest_unsupportedParameter_shouldThrowUnsupportedParameterException() {
        String json = """
                {"amount": 50, "locale": "en", "invalid": true}
                """;
        UnsupportedParameterException ex = assertThrows(UnsupportedParameterException.class,
                () -> underTest.validateLoanChargeAdjustmentRequest(1L, 2L, json));
        assertThat(ex.getUnsupportedParameters()).contains("invalid");
    }

    // -----------------------------------------------------------------------
    // validateInstallmentChargeTransaction
    // -----------------------------------------------------------------------

    @Test
    void validateInstallmentChargeTransaction_validPayload_shouldNotThrow() {
        String json = """
                {"installmentNumber": 1, "locale": "en"}
                """;
        assertDoesNotThrow(() -> underTest.validateInstallmentChargeTransaction(json));
    }

    @Test
    void validateInstallmentChargeTransaction_blankJson_shouldReturnSilently() {
        assertDoesNotThrow(() -> underTest.validateInstallmentChargeTransaction(""));
    }

    @Test
    void validateInstallmentChargeTransaction_nullJson_shouldReturnSilently() {
        assertDoesNotThrow(() -> underTest.validateInstallmentChargeTransaction(null));
    }

    @Test
    void validateInstallmentChargeTransaction_installmentNumberZero_shouldThrowValidationException() {
        String json = """
                {"installmentNumber": 0, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateInstallmentChargeTransaction(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("installmentNumber.not.greater.than.zero"));
    }

    @Test
    void validateInstallmentChargeTransaction_negativeInstallmentNumber_shouldThrowValidationException() {
        String json = """
                {"installmentNumber": -1, "locale": "en"}
                """;
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.validateInstallmentChargeTransaction(json));
        assertThat(ex.getErrors()).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("installmentNumber.not.greater.than.zero"));
    }

    @Test
    void validateInstallmentChargeTransaction_noInstallmentNumber_shouldNotThrow() {
        String json = """
                {"locale": "en"}
                """;
        assertDoesNotThrow(() -> underTest.validateInstallmentChargeTransaction(json));
    }

    @Test
    void validateInstallmentChargeTransaction_unsupportedParameter_shouldThrowUnsupportedParameterException() {
        String json = """
                {"installmentNumber": 1, "locale": "en", "extra": true}
                """;
        UnsupportedParameterException ex = assertThrows(UnsupportedParameterException.class,
                () -> underTest.validateInstallmentChargeTransaction(json));
        assertThat(ex.getUnsupportedParameters()).contains("extra");
    }

    // -----------------------------------------------------------------------
    // validateLoanCharges(Set<LoanCharge>, List<ApiParameterError>)
    // -----------------------------------------------------------------------

    @Test
    void validateLoanCharges_nullCharges_shouldNotAddErrors() {
        List<ApiParameterError> errors = new ArrayList<>();
        underTest.validateLoanCharges((Set<LoanCharge>) null, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void validateLoanCharges_emptyCharges_shouldNotAddErrors() {
        List<ApiParameterError> errors = new ArrayList<>();
        underTest.validateLoanCharges(new HashSet<>(), errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void validateLoanCharges_percentOfAmountInstallmentFee_shouldAddError() {
        LoanCharge loanCharge = mock(LoanCharge.class);
        given(loanCharge.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_AMOUNT);
        given(loanCharge.isInstalmentFee()).willReturn(true);

        List<ApiParameterError> errors = new ArrayList<>();
        underTest.validateLoanCharges(Set.of(loanCharge), errors);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("loancharge.with.calculation.type.principal.not.allowed"));
    }

    @Test
    void validateLoanCharges_percentOfAmountNotInstallmentFee_shouldNotAddError() {
        LoanCharge loanCharge = mock(LoanCharge.class);
        given(loanCharge.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_AMOUNT);
        given(loanCharge.isInstalmentFee()).willReturn(false);

        List<ApiParameterError> errors = new ArrayList<>();
        underTest.validateLoanCharges(Set.of(loanCharge), errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void validateLoanCharges_percentOfAmountAndInterestInstallmentFee_shouldAddPrincipalError() {
        LoanCharge loanCharge = mock(LoanCharge.class);
        given(loanCharge.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST);
        given(loanCharge.isInstalmentFee()).willReturn(true);

        List<ApiParameterError> errors = new ArrayList<>();
        underTest.validateLoanCharges(Set.of(loanCharge), errors);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("loancharge.with.calculation.type.principal.not.allowed"));
    }

    @Test
    void validateLoanCharges_percentOfAmountAndInterestSpecifiedDueDate_shouldAddInterestError() {
        LoanCharge loanCharge = mock(LoanCharge.class);
        given(loanCharge.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST);
        given(loanCharge.isInstalmentFee()).willReturn(false);
        given(loanCharge.isSpecifiedDueDate()).willReturn(true);

        List<ApiParameterError> errors = new ArrayList<>();
        underTest.validateLoanCharges(Set.of(loanCharge), errors);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("loancharge.with.calculation.type.interest.not.allowed"));
    }

    @Test
    void validateLoanCharges_percentOfInterestSpecifiedDueDate_shouldAddInterestError() {
        LoanCharge loanCharge = mock(LoanCharge.class);
        given(loanCharge.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_INTEREST);
        given(loanCharge.isSpecifiedDueDate()).willReturn(true);

        List<ApiParameterError> errors = new ArrayList<>();
        underTest.validateLoanCharges(Set.of(loanCharge), errors);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("loancharge.with.calculation.type.interest.not.allowed"));
    }

    @Test
    void validateLoanCharges_percentOfInterestInstallmentFeeProgressiveSchedule_shouldAddInterestError() {
        LoanCharge loanCharge = mock(LoanCharge.class);
        given(loanCharge.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_INTEREST);
        given(loanCharge.isSpecifiedDueDate()).willReturn(false);
        given(loanCharge.isInstalmentFee()).willReturn(true);
        Loan loan = mock(Loan.class);
        given(loanCharge.getLoan()).willReturn(loan);
        given(loan.isProgressiveSchedule()).willReturn(true);

        List<ApiParameterError> errors = new ArrayList<>();
        underTest.validateLoanCharges(Set.of(loanCharge), errors);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("loancharge.with.calculation.type.interest.not.allowed"));
    }

    @Test
    void validateLoanCharges_percentOfInterestInstallmentFeeNonProgressiveSchedule_shouldNotAddError() {
        LoanCharge loanCharge = mock(LoanCharge.class);
        given(loanCharge.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_INTEREST);
        given(loanCharge.isSpecifiedDueDate()).willReturn(false);
        given(loanCharge.isInstalmentFee()).willReturn(true);
        Loan loan = mock(Loan.class);
        given(loanCharge.getLoan()).willReturn(loan);
        given(loan.isProgressiveSchedule()).willReturn(false);

        List<ApiParameterError> errors = new ArrayList<>();
        underTest.validateLoanCharges(Set.of(loanCharge), errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void validateLoanCharges_defaultCalculationType_shouldNotAddError() {
        LoanCharge loanCharge = mock(LoanCharge.class);
        given(loanCharge.getChargeCalculation()).willReturn(ChargeCalculationType.FLAT);

        List<ApiParameterError> errors = new ArrayList<>();
        underTest.validateLoanCharges(Set.of(loanCharge), errors);
        assertThat(errors).isEmpty();
    }

    // -----------------------------------------------------------------------
    // validateLoanCharges(JsonElement, LoanProduct, DataValidatorBuilder)
    // — chargeId-based path
    // -----------------------------------------------------------------------

    @Test
    void validateLoanChargesJson_noChargesParameter_shouldNotAddErrors() {
        JsonElement element = fromJsonHelper.parse("""
                {"amount": 1000, "locale": "en"}
                """);
        LoanProduct loanProduct = mock(LoanProduct.class);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).isEmpty();
    }

    @Test
    void validateLoanChargesJson_chargeIdBasedValidCharge_shouldNotAddErrors() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.DISBURSEMENT.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.FLAT.getValue());
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);
        given(loanProduct.isInterestRecalculationEnabled()).willReturn(false);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 100}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).isEmpty();
    }

    @Test
    void validateLoanChargesJson_currencyMismatch_shouldThrowInvalidCurrencyException() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("EUR");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.DISBURSEMENT.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.FLAT.getValue());
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("EUR")).willReturn(false);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 100}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        assertThrows(InvalidCurrencyException.class, () -> underTest.validateLoanCharges(element, loanProduct, baseDataValidator));
    }

    @Test
    void validateLoanChargesJson_overdueInstallmentCharge_shouldThrowLoanChargeCannotBeAddedException() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.OVERDUE_INSTALLMENT.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.FLAT.getValue());
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(true);
        given(chargeDefinition.getName()).willReturn("Overdue Charge");
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 100}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        assertThrows(LoanChargeCannotBeAddedException.class,
                () -> underTest.validateLoanCharges(element, loanProduct, baseDataValidator));
    }

    @Test
    void validateLoanChargesJson_specifiedDueDateBeforeDisbursement_shouldThrowLoanChargeCannotBeAddedException() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.FLAT.getValue());
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);
        given(chargeDefinition.getName()).willReturn("Specified Due Date Charge");
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 100, "dueDate": "01 January 2025"}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        assertThrows(LoanChargeCannotBeAddedException.class,
                () -> underTest.validateLoanCharges(element, loanProduct, baseDataValidator));
    }

    @Test
    void validateLoanChargesJson_paymentModeAccountTransferWithoutLinkedAccount_shouldThrowLinkedAccountRequiredException() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.DISBURSEMENT.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.FLAT.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 100, "chargePaymentMode": 1}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        assertThrows(LinkedAccountRequiredException.class,
                () -> underTest.validateLoanCharges(element, loanProduct, baseDataValidator));
    }

    // -----------------------------------------------------------------------
    // validateLoanCharges(JsonElement, ...) — loanChargeId-based path
    // -----------------------------------------------------------------------

    @Test
    void validateLoanChargesJson_loanChargeIdBasedValidCharge_shouldNotAddErrors() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);

        LoanCharge loanCharge = mock(LoanCharge.class);
        given(loanCharge.getCharge()).willReturn(chargeDefinition);
        given(loanCharge.getChargeTimeType()).willReturn(ChargeTimeType.DISBURSEMENT);
        given(loanCharge.getChargeCalculation()).willReturn(ChargeCalculationType.FLAT);
        given(loanCharge.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR);
        given(loanChargeRepository.findById(anyLong())).willReturn(Optional.of(loanCharge));

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);
        given(loanProduct.isInterestRecalculationEnabled()).willReturn(false);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "id": 10,
                    "charges": [{"id": 10, "amount": 100}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).isEmpty();
    }

    @Test
    void validateLoanChargesJson_missingBothChargeIdAndLoanChargeId_shouldReturnEarly() {
        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"amount": 100}],
                    "locale": "en"
                }
                """);
        LoanProduct loanProduct = mock(LoanProduct.class);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("chargeId.cannot.be.blank"));
    }

    @Test
    void validateLoanChargesJson_missingAmountInChargeArray_shouldAddAmountError() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.DISBURSEMENT.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.FLAT.getValue());
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);
        given(loanProduct.isInterestRecalculationEnabled()).willReturn(false);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode().contains("amount"));
    }

    // -----------------------------------------------------------------------
    // validateInterestBearingLoanProductRestriction (via validateLoanCharges)
    // -----------------------------------------------------------------------

    @Test
    void validateLoanChargesJson_interestRecalcEnabled_percentOfAmountInstallmentFee_shouldAddError() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.INSTALMENT_FEE.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_AMOUNT.getValue());
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);
        given(loanProduct.isInterestRecalculationEnabled()).willReturn(true);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 5}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode()
                .contains("loancharge.with.calculation.type.principal.not.allowed"));
    }

    @Test
    void validateLoanChargesJson_interestRecalcEnabled_percentOfAmountAndInterestInstallmentFee_shouldAddPrincipalError() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.INSTALMENT_FEE.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST.getValue());
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);
        given(loanProduct.isInterestRecalculationEnabled()).willReturn(true);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 5}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode()
                .contains("loancharge.with.calculation.type.principal.not.allowed"));
    }

    @Test
    void validateLoanChargesJson_interestRecalcEnabled_percentOfAmountAndInterestSpecifiedDueDate_shouldAddInterestError() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST.getValue());
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);
        given(loanProduct.isInterestRecalculationEnabled()).willReturn(true);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 5, "dueDate": "20 February 2025"}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode()
                .contains("loancharge.with.calculation.type.interest.not.allowed"));
    }

    @Test
    void validateLoanChargesJson_interestRecalcEnabled_percentOfInterestSpecifiedDueDate_shouldAddInterestError() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_INTEREST.getValue());
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);
        given(loanProduct.isInterestRecalculationEnabled()).willReturn(true);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 5, "dueDate": "20 February 2025"}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode()
                .contains("loancharge.with.calculation.type.interest.not.allowed"));
    }

    @Test
    void validateLoanChargesJson_interestRecalcEnabled_percentOfInterestInstallmentFeeProgressiveSchedule_shouldAddInterestError() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.INSTALMENT_FEE.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.PERCENT_OF_INTEREST.getValue());
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);
        given(loanProduct.isInterestRecalculationEnabled()).willReturn(true);
        LoanProductRelatedDetail relatedDetail = mock(LoanProductRelatedDetail.class);
        given(loanProduct.getLoanProductRelatedDetail()).willReturn(relatedDetail);
        given(relatedDetail.getLoanScheduleType()).willReturn(LoanScheduleType.PROGRESSIVE);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 5}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode()
                .contains("loancharge.with.calculation.type.interest.not.allowed"));
    }

    @Test
    void validateLoanChargesJson_interestRecalcEnabled_flatCalculation_shouldNotAddError() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargeTimeType()).willReturn(ChargeTimeType.DISBURSEMENT.getValue());
        given(chargeDefinition.getChargeCalculation()).willReturn(ChargeCalculationType.FLAT.getValue());
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);
        given(loanProduct.isInterestRecalculationEnabled()).willReturn(true);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 100}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).isEmpty();
    }

    @Test
    void validateLoanChargesJson_nonJsonObject_shouldNotAddErrors() {
        JsonElement element = fromJsonHelper.parse("\"just a string\"");
        LoanProduct loanProduct = mock(LoanProduct.class);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).isEmpty();
    }

    @Test
    void validateLoanChargesJson_chargeTimeTypeOverriddenInPayload_shouldUsePayloadValue() {
        Charge chargeDefinition = mock(Charge.class);
        given(chargeDefinition.getCurrencyCode()).willReturn("USD");
        given(chargeDefinition.getChargePaymentMode()).willReturn(ChargePaymentMode.REGULAR.getValue());
        given(chargeDefinition.isOverdueInstallment()).willReturn(false);
        given(chargeRepository.findOneWithNotFoundDetection(1L)).willReturn(chargeDefinition);

        LoanProduct loanProduct = mock(LoanProduct.class);
        given(loanProduct.hasCurrencyCodeOf("USD")).willReturn(true);
        given(loanProduct.isInterestRecalculationEnabled()).willReturn(true);

        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 5, "chargeTimeType": 8, "chargeCalculationType": 2}],
                    "locale": "en",
                    "dateFormat": "dd MMMM yyyy",
                    "expectedDisbursementDate": "15 January 2025"
                }
                """);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        underTest.validateLoanCharges(element, loanProduct, baseDataValidator);
        assertThat(errors).anyMatch(e -> e.getUserMessageGlobalisationCode()
                .contains("loancharge.with.calculation.type.principal.not.allowed"));
    }

    @Test
    void validateLoanChargesJson_unsupportedParameterInChargeArray_shouldThrowUnsupportedParameterException() {
        JsonElement element = fromJsonHelper.parse("""
                {
                    "charges": [{"chargeId": 1, "amount": 100, "bogus": "value"}],
                    "locale": "en"
                }
                """);
        LoanProduct loanProduct = mock(LoanProduct.class);
        List<ApiParameterError> errors = new ArrayList<>();
        DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");

        assertThrows(UnsupportedParameterException.class,
                () -> underTest.validateLoanCharges(element, loanProduct, baseDataValidator));
    }
}
