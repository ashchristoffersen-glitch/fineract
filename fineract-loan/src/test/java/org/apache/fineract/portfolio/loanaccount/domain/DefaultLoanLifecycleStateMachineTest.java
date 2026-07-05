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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.event.business.domain.loan.LoanStatusChangedBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.service.LoanBalanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultLoanLifecycleStateMachineTest {

    private static final MonetaryCurrency CURRENCY = new MonetaryCurrency("USD", 2, null);

    @Mock
    private BusinessEventNotifierService businessEventNotifierService;

    @Mock
    private LoanBalanceService loanBalanceService;

    private DefaultLoanLifecycleStateMachine underTest;

    private MockedStatic<MoneyHelper> moneyHelperStatic;

    @BeforeEach
    public void setUp() {
        moneyHelperStatic = Mockito.mockStatic(MoneyHelper.class);
        moneyHelperStatic.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.UP));
        moneyHelperStatic.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.UP);
        underTest = new DefaultLoanLifecycleStateMachine(businessEventNotifierService, loanBalanceService);
    }

    @AfterEach
    public void tearDown() {
        ThreadLocalContextUtil.reset();
        moneyHelperStatic.close();
    }

    // ========== valid transition table (simple cases usable with a real Loan) ==========

    static Stream<Arguments> validTransitions() {
        return Stream.of( //
                Arguments.of(LoanEvent.LOAN_REJECTED, LoanStatus.SUBMITTED_AND_PENDING_APPROVAL, LoanStatus.REJECTED),
                Arguments.of(LoanEvent.LOAN_APPROVED, LoanStatus.SUBMITTED_AND_PENDING_APPROVAL, LoanStatus.APPROVED),
                Arguments.of(LoanEvent.LOAN_WITHDRAWN, LoanStatus.SUBMITTED_AND_PENDING_APPROVAL, LoanStatus.WITHDRAWN_BY_CLIENT),
                Arguments.of(LoanEvent.LOAN_DISBURSED, LoanStatus.APPROVED, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_DISBURSED, LoanStatus.CLOSED_OBLIGATIONS_MET, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_APPROVAL_UNDO, LoanStatus.APPROVED, LoanStatus.SUBMITTED_AND_PENDING_APPROVAL),
                Arguments.of(LoanEvent.LOAN_DISBURSAL_UNDO, LoanStatus.ACTIVE, LoanStatus.APPROVED),
                Arguments.of(LoanEvent.LOAN_CHARGE_PAYMENT, LoanStatus.CLOSED_OBLIGATIONS_MET, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_CHARGE_PAYMENT, LoanStatus.OVERPAID, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_REPAYMENT_OR_WAIVER, LoanStatus.CLOSED_OBLIGATIONS_MET, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_REPAYMENT_OR_WAIVER, LoanStatus.OVERPAID, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_CHARGEBACK, LoanStatus.CLOSED_OBLIGATIONS_MET, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_CHARGEBACK, LoanStatus.OVERPAID, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.REPAID_IN_FULL, LoanStatus.ACTIVE, LoanStatus.CLOSED_OBLIGATIONS_MET),
                Arguments.of(LoanEvent.REPAID_IN_FULL, LoanStatus.OVERPAID, LoanStatus.CLOSED_OBLIGATIONS_MET),
                Arguments.of(LoanEvent.WRITE_OFF_OUTSTANDING, LoanStatus.ACTIVE, LoanStatus.CLOSED_WRITTEN_OFF),
                Arguments.of(LoanEvent.LOAN_RESCHEDULE, LoanStatus.ACTIVE, LoanStatus.CLOSED_RESCHEDULE_OUTSTANDING_AMOUNT),
                Arguments.of(LoanEvent.LOAN_OVERPAYMENT, LoanStatus.CLOSED_OBLIGATIONS_MET, LoanStatus.OVERPAID),
                Arguments.of(LoanEvent.LOAN_OVERPAYMENT, LoanStatus.ACTIVE, LoanStatus.OVERPAID),
                Arguments.of(LoanEvent.LOAN_ADJUST_TRANSACTION, LoanStatus.CLOSED_OBLIGATIONS_MET, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_ADJUST_TRANSACTION, LoanStatus.CLOSED_WRITTEN_OFF, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_ADJUST_TRANSACTION, LoanStatus.CLOSED_RESCHEDULE_OUTSTANDING_AMOUNT, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_INITIATE_TRANSFER, LoanStatus.ACTIVE, LoanStatus.TRANSFER_IN_PROGRESS),
                Arguments.of(LoanEvent.LOAN_REJECT_TRANSFER, LoanStatus.TRANSFER_IN_PROGRESS, LoanStatus.TRANSFER_ON_HOLD),
                Arguments.of(LoanEvent.LOAN_WITHDRAW_TRANSFER, LoanStatus.TRANSFER_IN_PROGRESS, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.WRITE_OFF_OUTSTANDING_UNDO, LoanStatus.CLOSED_WRITTEN_OFF, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_CREDIT_BALANCE_REFUND, LoanStatus.OVERPAID, LoanStatus.CLOSED_OBLIGATIONS_MET),
                Arguments.of(LoanEvent.LOAN_CHARGE_ADDED, LoanStatus.CLOSED_OBLIGATIONS_MET, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_CHARGE_ADJUSTMENT, LoanStatus.CLOSED_OBLIGATIONS_MET, LoanStatus.OVERPAID));
    }

    @ParameterizedTest(name = "{0} from {1} -> {2}")
    @MethodSource("validTransitions")
    public void transitionAppliesExpectedStatusAndNotifies(LoanEvent event, LoanStatus from, LoanStatus expected) {
        Loan loan = loanWithStatus(from);

        underTest.transition(event, loan);

        assertThat(loan.getStatus()).isEqualTo(expected);
        verify(loanBalanceService).updateLoanSummaryDerivedFields(loan);
        verify(businessEventNotifierService).notifyPostBusinessEvent(any(LoanStatusChangedBusinessEvent.class));
    }

    @ParameterizedTest(name = "{0} from {1} -> {2}")
    @MethodSource("validTransitions")
    public void dryTransitionReturnsExpectedStatusWithoutMutating(LoanEvent event, LoanStatus from, LoanStatus expected) {
        Loan loan = loanWithStatus(from);

        LoanStatus result = underTest.dryTransition(event, loan);

        assertThat(result).isEqualTo(expected);
        assertThat(loan.getStatus()).isEqualTo(from);
        verifyNoInteractions(businessEventNotifierService, loanBalanceService);
    }

    // ========== invalid / no-op transitions ==========

    static Stream<Arguments> invalidTransitions() {
        return Stream.of( //
                Arguments.of(LoanEvent.LOAN_REJECTED, LoanStatus.ACTIVE), Arguments.of(LoanEvent.LOAN_APPROVED, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_WITHDRAWN, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_DISBURSED, LoanStatus.SUBMITTED_AND_PENDING_APPROVAL),
                Arguments.of(LoanEvent.LOAN_APPROVAL_UNDO, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_DISBURSAL_UNDO, LoanStatus.SUBMITTED_AND_PENDING_APPROVAL),
                Arguments.of(LoanEvent.REPAID_IN_FULL, LoanStatus.SUBMITTED_AND_PENDING_APPROVAL),
                Arguments.of(LoanEvent.WRITE_OFF_OUTSTANDING, LoanStatus.CLOSED_OBLIGATIONS_MET),
                Arguments.of(LoanEvent.WRITE_OFF_OUTSTANDING_UNDO, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_RESCHEDULE, LoanStatus.APPROVED),
                Arguments.of(LoanEvent.LOAN_OVERPAYMENT, LoanStatus.SUBMITTED_AND_PENDING_APPROVAL),
                Arguments.of(LoanEvent.LOAN_ADJUST_TRANSACTION, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_REJECT_TRANSFER, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_WITHDRAW_TRANSFER, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_COMPLETE_TRANSFER, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_CREDIT_BALANCE_REFUND, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_CHARGE_ADDED, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_CHARGE_ADJUSTMENT, LoanStatus.ACTIVE),
                Arguments.of(LoanEvent.LOAN_RECOVERY_PAYMENT, LoanStatus.ACTIVE), Arguments.of(LoanEvent.LOAN_CLOSED, LoanStatus.ACTIVE));
    }

    @ParameterizedTest(name = "{0} from {1} is a no-op")
    @MethodSource("invalidTransitions")
    public void transitionDoesNothingForInvalidEvent(LoanEvent event, LoanStatus from) {
        Loan loan = loanWithStatus(from);

        underTest.transition(event, loan);

        assertThat(loan.getStatus()).isEqualTo(from);
        verify(loanBalanceService).updateLoanSummaryDerivedFields(loan);
        verifyNoInteractions(businessEventNotifierService);
    }

    @ParameterizedTest(name = "{0} from {1} is a no-op")
    @MethodSource("invalidTransitions")
    public void dryTransitionReturnsCurrentStatusForInvalidEvent(LoanEvent event, LoanStatus from) {
        Loan loan = loanWithStatus(from);

        LoanStatus result = underTest.dryTransition(event, loan);

        assertThat(result).isEqualTo(from);
        assertThat(loan.getStatus()).isEqualTo(from);
        verifyNoInteractions(businessEventNotifierService, loanBalanceService);
    }

    // ========== loan creation (no status-change event fired) ==========

    @Test
    public void transitionForLoanCreationDoesNotNotify() {
        Loan loan = loanWithStatus(null);

        underTest.transition(LoanEvent.LOAN_CREATED, loan);

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        verify(loanBalanceService).updateLoanSummaryDerivedFields(loan);
        verifyNoInteractions(businessEventNotifierService);
    }

    @Test
    public void dryTransitionForLoanCreationReturnsSubmitted() {
        Loan loan = loanWithStatus(null);

        LoanStatus result = underTest.dryTransition(LoanEvent.LOAN_CREATED, loan);

        assertThat(result).isEqualTo(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        assertThat(loan.getStatus()).isNull();
    }

    // ========== mandatory field clearing after transition ==========

    @Test
    public void transitionToSubmittedClearsApprovalFields() {
        Loan loan = mockLoanWithStatus(LoanStatus.APPROVED);

        underTest.transition(LoanEvent.LOAN_APPROVAL_UNDO, loan);

        verify(loan).setLoanStatus(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);
        verify(loan).setApprovedOnDate(null);
        verify(loan).setApprovedBy(null);
    }

    @Test
    public void transitionToApprovedClearsDisbursementFields() {
        Loan loan = mockLoanWithStatus(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL);

        underTest.transition(LoanEvent.LOAN_APPROVED, loan);

        verify(loan).setLoanStatus(LoanStatus.APPROVED);
        verify(loan).setDisbursedBy(null);
        verify(loan).setActualDisbursementDate(null);
    }

    @Test
    public void transitionToActiveClearsClosureFields() {
        Loan loan = mockLoanWithStatus(LoanStatus.APPROVED);

        underTest.transition(LoanEvent.LOAN_DISBURSED, loan);

        verify(loan).setLoanStatus(LoanStatus.ACTIVE);
        verify(loan).setClosedBy(null);
        verify(loan).setClosedOnDate(null);
        verify(loan).setOverpaidOnDate(null);
    }

    // ========== disbursement from overpaid (financial-state dependent) ==========

    @Test
    public void transitionDisbursedFromOverpaidBecomesActiveWhenOutstandingRemains() {
        Money zero = Money.of(CURRENCY, BigDecimal.ZERO);
        Money one = Money.of(CURRENCY, BigDecimal.ONE);
        Loan loan = Mockito.mock(Loan.class);
        LoanSummary summary = Mockito.mock(LoanSummary.class);
        when(loan.getStatus()).thenReturn(LoanStatus.OVERPAID);
        when(loan.getCurrency()).thenReturn(CURRENCY);
        when(loan.getTotalOverpaidAsMoney()).thenReturn(zero);
        when(loan.getSummary()).thenReturn(summary);
        when(summary.getTotalOutstanding(eq(CURRENCY))).thenReturn(one);

        underTest.transition(LoanEvent.LOAN_DISBURSED, loan);

        verify(loan).setLoanStatus(LoanStatus.ACTIVE);
        verify(businessEventNotifierService).notifyPostBusinessEvent(any(LoanStatusChangedBusinessEvent.class));
    }

    @Test
    public void transitionDisbursedFromOverpaidBecomesClosedWhenNothingOutstanding() {
        Money zero = Money.of(CURRENCY, BigDecimal.ZERO);
        Loan loan = Mockito.mock(Loan.class);
        LoanSummary summary = Mockito.mock(LoanSummary.class);
        when(loan.getStatus()).thenReturn(LoanStatus.OVERPAID);
        when(loan.getCurrency()).thenReturn(CURRENCY);
        when(loan.getTotalOverpaidAsMoney()).thenReturn(zero);
        when(loan.getSummary()).thenReturn(summary);
        when(summary.getTotalOutstanding(eq(CURRENCY))).thenReturn(zero);

        underTest.transition(LoanEvent.LOAN_DISBURSED, loan);

        verify(loan).setLoanStatus(LoanStatus.CLOSED_OBLIGATIONS_MET);
        verify(businessEventNotifierService).notifyPostBusinessEvent(any(LoanStatusChangedBusinessEvent.class));
    }

    @Test
    public void transitionDisbursedFromOverpaidStaysOverpaidWhenStillOverpaid() {
        Money ten = Money.of(CURRENCY, BigDecimal.TEN);
        Loan loan = Mockito.mock(Loan.class);
        when(loan.getStatus()).thenReturn(LoanStatus.OVERPAID);
        when(loan.getTotalOverpaidAsMoney()).thenReturn(ten);

        underTest.transition(LoanEvent.LOAN_DISBURSED, loan);

        verify(loan, never()).setLoanStatus(any());
        verifyNoInteractions(businessEventNotifierService);
    }

    // ========== adjust transaction that flips loan to overpaid ==========

    @Test
    public void transitionAdjustTransactionFromClosedBecomesOverpaidWhenOverpaid() {
        Loan loan = Mockito.mock(Loan.class);
        when(loan.getStatus()).thenReturn(LoanStatus.CLOSED_WRITTEN_OFF);
        when(loan.getTotalOverpaid()).thenReturn(BigDecimal.TEN);

        underTest.transition(LoanEvent.LOAN_ADJUST_TRANSACTION, loan);

        verify(loan).setLoanStatus(LoanStatus.OVERPAID);
        verify(businessEventNotifierService).notifyPostBusinessEvent(any(LoanStatusChangedBusinessEvent.class));
    }

    // ========== complete transfer (three outcomes) ==========

    @Test
    public void transitionCompleteTransferBecomesActiveWhenNotFullyPaid() {
        Loan loan = Mockito.mock(Loan.class);
        LoanSummary summary = Mockito.mock(LoanSummary.class);
        when(loan.getStatus()).thenReturn(LoanStatus.TRANSFER_IN_PROGRESS);
        when(loan.getTotalOverpaid()).thenReturn(null);
        when(loan.getSummary()).thenReturn(summary);
        when(loan.getCurrency()).thenReturn(CURRENCY);
        when(summary.isRepaidInFull(CURRENCY)).thenReturn(false);

        underTest.transition(LoanEvent.LOAN_COMPLETE_TRANSFER, loan);

        verify(loan).setLoanStatus(LoanStatus.ACTIVE);
        verify(businessEventNotifierService).notifyPostBusinessEvent(any(LoanStatusChangedBusinessEvent.class));
    }

    @Test
    public void transitionCompleteTransferBecomesClosedWhenFullyPaid() {
        Loan loan = Mockito.mock(Loan.class);
        LoanSummary summary = Mockito.mock(LoanSummary.class);
        when(loan.getStatus()).thenReturn(LoanStatus.TRANSFER_IN_PROGRESS);
        when(loan.getTotalOverpaid()).thenReturn(null);
        when(loan.getSummary()).thenReturn(summary);
        when(loan.getCurrency()).thenReturn(CURRENCY);
        when(summary.isRepaidInFull(CURRENCY)).thenReturn(true);

        underTest.transition(LoanEvent.LOAN_COMPLETE_TRANSFER, loan);

        verify(loan).setLoanStatus(LoanStatus.CLOSED_OBLIGATIONS_MET);
        verify(businessEventNotifierService).notifyPostBusinessEvent(any(LoanStatusChangedBusinessEvent.class));
    }

    @Test
    public void transitionCompleteTransferBecomesOverpaidWhenOverpaid() {
        Loan loan = Mockito.mock(Loan.class);
        when(loan.getStatus()).thenReturn(LoanStatus.TRANSFER_IN_PROGRESS);
        when(loan.getTotalOverpaid()).thenReturn(BigDecimal.ONE);

        underTest.transition(LoanEvent.LOAN_COMPLETE_TRANSFER, loan);

        verify(loan).setLoanStatus(LoanStatus.OVERPAID);
        verify(businessEventNotifierService).notifyPostBusinessEvent(any(LoanStatusChangedBusinessEvent.class));
    }

    // ========== determineAndTransition ==========

    @Test
    public void determineAndTransitionDoesNothingWhenStatusNull() {
        Loan loan = Mockito.mock(Loan.class);
        when(loan.getStatus()).thenReturn(null);

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loanBalanceService, never()).updateLoanSummaryDerivedFields(any());
        verify(loan, never()).setLoanStatus(any());
        verifyNoInteractions(businessEventNotifierService);
    }

    @Test
    public void determineAndTransitionFromOverpaidToClosedWhenObligationsMet() {
        Loan loan = mockFinancialLoan(LoanStatus.OVERPAID, BigDecimal.ZERO, true, false, Set.of(paidCharge()));

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan).setLoanStatus(LoanStatus.CLOSED_OBLIGATIONS_MET);
    }

    @Test
    public void determineAndTransitionFromOverpaidToActiveWhenOutstanding() {
        Loan loan = mockFinancialLoan(LoanStatus.OVERPAID, BigDecimal.ZERO, false, true, Set.of());

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan).setLoanStatus(LoanStatus.ACTIVE);
    }

    @Test
    public void determineAndTransitionFromOverpaidStaysWhenStillOverpaid() {
        Loan loan = mockFinancialLoan(LoanStatus.OVERPAID, BigDecimal.TEN, false, true, Set.of());

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan, never()).setLoanStatus(any());
        verifyNoInteractions(businessEventNotifierService);
    }

    @Test
    public void determineAndTransitionFromClosedToOverpaid() {
        Loan loan = mockFinancialLoan(LoanStatus.CLOSED_OBLIGATIONS_MET, BigDecimal.TEN, false, false, Set.of());

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan).setLoanStatus(LoanStatus.OVERPAID);
    }

    @Test
    public void determineAndTransitionFromClosedToActiveWhenOutstanding() {
        Loan loan = mockFinancialLoan(LoanStatus.CLOSED_OBLIGATIONS_MET, BigDecimal.ZERO, false, true, Set.of());

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan).setLoanStatus(LoanStatus.ACTIVE);
    }

    @Test
    public void determineAndTransitionFromClosedStaysWhenSettled() {
        Loan loan = mockFinancialLoan(LoanStatus.CLOSED_OBLIGATIONS_MET, BigDecimal.ZERO, false, false, Set.of());

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan, never()).setLoanStatus(any());
    }

    @Test
    public void determineAndTransitionFromActiveToOverpaid() {
        Loan loan = mockFinancialLoan(LoanStatus.ACTIVE, BigDecimal.TEN, false, false, Set.of());

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan).setLoanStatus(LoanStatus.OVERPAID);
    }

    @Test
    public void determineAndTransitionFromActiveToClosedWhenRepaidInFull() {
        Loan loan = mockFinancialLoan(LoanStatus.ACTIVE, BigDecimal.ZERO, true, false, Set.of(paidCharge()));

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan).setLoanStatus(LoanStatus.CLOSED_OBLIGATIONS_MET);
    }

    @Test
    public void determineAndTransitionFromActiveStaysWhenOutstanding() {
        Loan loan = mockFinancialLoan(LoanStatus.ACTIVE, BigDecimal.ZERO, false, true, Set.of());

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan, never()).setLoanStatus(any());
    }

    @Test
    public void determineAndTransitionFromWrittenOffToActiveWhenOutstanding() {
        Loan loan = mockFinancialLoan(LoanStatus.CLOSED_WRITTEN_OFF, BigDecimal.ZERO, false, true, Set.of());

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan).setLoanStatus(LoanStatus.ACTIVE);
    }

    @Test
    public void determineAndTransitionFromWrittenOffStaysWhenNoOutstanding() {
        Loan loan = mockFinancialLoan(LoanStatus.CLOSED_WRITTEN_OFF, BigDecimal.ZERO, false, false, Set.of());

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan, never()).setLoanStatus(any());
    }

    @Test
    public void determineAndTransitionFromRescheduledToActiveWhenOutstanding() {
        Loan loan = mockFinancialLoan(LoanStatus.CLOSED_RESCHEDULE_OUTSTANDING_AMOUNT, BigDecimal.ZERO, false, true, Set.of());

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan).setLoanStatus(LoanStatus.ACTIVE);
    }

    @Test
    public void determineAndTransitionFromUnhandledStatusIsNoOp() {
        Loan loan = mockFinancialLoan(LoanStatus.SUBMITTED_AND_PENDING_APPROVAL, BigDecimal.ZERO, false, false, Set.of());

        underTest.determineAndTransition(loan, LocalDate.of(2024, 1, 1));

        verify(loan, never()).setLoanStatus(any());
    }

    // ========== helpers ==========

    private Loan loanWithStatus(LoanStatus status) {
        Loan loan = new Loan();
        loan.setLoanStatus(status);
        return loan;
    }

    private Loan mockLoanWithStatus(LoanStatus status) {
        Loan loan = Mockito.mock(Loan.class);
        when(loan.getStatus()).thenReturn(status);
        return loan;
    }

    private Loan mockFinancialLoan(LoanStatus status, BigDecimal totalOverpaid, boolean repaidInFull, boolean hasOutstanding,
            Set<LoanCharge> charges) {
        Money outstanding = Money.of(CURRENCY, hasOutstanding ? BigDecimal.ONE : BigDecimal.ZERO);
        Loan loan = Mockito.mock(Loan.class);
        LoanSummary summary = Mockito.mock(LoanSummary.class);
        when(loan.getStatus()).thenReturn(status);
        when(loan.getCurrency()).thenReturn(CURRENCY);
        when(loan.getTotalOverpaid()).thenReturn(totalOverpaid);
        when(loan.getSummary()).thenReturn(summary);
        when(loan.getLoanCharges()).thenReturn(charges);
        when(summary.isRepaidInFull(CURRENCY)).thenReturn(repaidInFull);
        when(summary.getTotalOutstanding(CURRENCY)).thenReturn(outstanding);
        return loan;
    }

    private LoanCharge paidCharge() {
        LoanCharge charge = Mockito.mock(LoanCharge.class);
        when(charge.isActive()).thenReturn(true);
        when(charge.amount()).thenReturn(BigDecimal.ONE);
        when(charge.isPaid()).thenReturn(true);
        return charge;
    }
}
