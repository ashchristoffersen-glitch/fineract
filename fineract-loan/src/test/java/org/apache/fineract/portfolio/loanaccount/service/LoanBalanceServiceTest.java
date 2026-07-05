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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.persistence.FlushModeHandler;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSummary;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoanBalanceServiceTest {

    private static final MonetaryCurrency USD = new MonetaryCurrency("USD", 2, null);
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2024, 1, 15);

    @Mock
    private CapitalizedIncomeBalanceService capitalizedIncomeBalanceService;
    @Mock
    private FlushModeHandler flushModeHandler;
    @Mock
    private LoanTransactionRepository loanTransactionRepository;

    private LoanBalanceService underTest;

    private MockedStatic<MoneyHelper> moneyHelperStatic;

    @BeforeEach
    void setUp() {
        moneyHelperStatic = Mockito.mockStatic(MoneyHelper.class);
        moneyHelperStatic.when(MoneyHelper::getMathContext).thenReturn(new MathContext(12, RoundingMode.HALF_EVEN));
        moneyHelperStatic.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kolkata", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
        underTest = new LoanBalanceService(capitalizedIncomeBalanceService, flushModeHandler, loanTransactionRepository);
    }

    @AfterEach
    void tearDown() {
        moneyHelperStatic.close();
        ThreadLocalContextUtil.reset();
    }

    // ----- calculateTotalOverpayment / isOverPaid -----

    @Test
    void calculateTotalOverpayment_returnsPositiveWhenPaidMoreThanScheduled() {
        Loan loan = newLoan(bd(100));
        LoanRepaymentScheduleInstallment installment = installment(loan, 1, date(2024, 1, 1), date(2024, 2, 1), bd(100), bd(0), bd(0),
                bd(0));
        installment.setPrincipalCompleted(bd(100));
        setInstallments(loan, installment);
        setTransactions(loan, repayment(loan, date(2024, 2, 1), bd(120), bd(120)));

        Money overpayment = underTest.calculateTotalOverpayment(loan);

        assertAmount("20", overpayment);
        assertTrue(underTest.isOverPaid(loan));
    }

    @Test
    void calculateTotalOverpayment_returnsZeroWhenFullyPaid() {
        Loan loan = newLoan(bd(100));
        LoanRepaymentScheduleInstallment installment = installment(loan, 1, date(2024, 1, 1), date(2024, 2, 1), bd(100), bd(0), bd(0),
                bd(0));
        installment.setPrincipalCompleted(bd(100));
        setInstallments(loan, installment);
        setTransactions(loan, repayment(loan, date(2024, 2, 1), bd(100), bd(100)));

        Money overpayment = underTest.calculateTotalOverpayment(loan);

        assertAmount("0", overpayment);
        assertFalse(underTest.isOverPaid(loan));
    }

    @Test
    void calculateTotalOverpayment_isNegativeWhenPartiallyPaid() {
        Loan loan = newLoan(bd(100));
        LoanRepaymentScheduleInstallment installment = installment(loan, 1, date(2024, 1, 1), date(2024, 2, 1), bd(100), bd(0), bd(0),
                bd(0));
        installment.setPrincipalCompleted(bd(100));
        setInstallments(loan, installment);
        setTransactions(loan, repayment(loan, date(2024, 2, 1), bd(60), bd(60)));

        Money overpayment = underTest.calculateTotalOverpayment(loan);

        assertAmount("-40", overpayment);
        assertFalse(underTest.isOverPaid(loan));
    }

    @Test
    void calculateTotalOverpayment_freshlyDisbursedHasNoOverpayment() {
        Loan loan = newLoan(bd(100));
        setInstallments(loan);
        setTransactions(loan, tx(loan, LoanTransactionType.DISBURSEMENT, date(2024, 1, 1), bd(100), bd(0), bd(0), bd(0), bd(0), bd(0),
                false));

        Money overpayment = underTest.calculateTotalOverpayment(loan);

        assertAmount("0", overpayment);
    }

    @Test
    void calculateTotalOverpayment_refundAndRefundForActiveLoanReducePaidAmount() {
        Loan loan = newLoan(bd(100));
        setInstallments(loan);
        setTransactions(loan, //
                repayment(loan, date(2024, 2, 1), bd(100), bd(100)), //
                tx(loan, LoanTransactionType.REFUND, date(2024, 2, 2), bd(30), bd(30), bd(0), bd(0), bd(0), bd(0), false));

        Money overpayment = underTest.calculateTotalOverpayment(loan);

        assertAmount("70", overpayment);
    }

    @Test
    void calculateTotalOverpayment_reversedTransactionsAreIgnored() {
        Loan loan = newLoan(bd(100));
        setInstallments(loan);
        setTransactions(loan, //
                repayment(loan, date(2024, 2, 1), bd(100), bd(100)), //
                tx(loan, LoanTransactionType.REFUND, date(2024, 2, 2), bd(30), bd(30), bd(0), bd(0), bd(0), bd(0), true));

        Money overpayment = underTest.calculateTotalOverpayment(loan);

        assertAmount("100", overpayment);
    }

    @Test
    void calculateTotalOverpayment_creditBalanceRefundWithZeroPrincipalReducesByOverpayment() {
        Loan loan = newLoan(bd(100));
        setInstallments(loan);
        setTransactions(loan, //
                repayment(loan, date(2024, 2, 1), bd(100), bd(100)), //
                tx(loan, LoanTransactionType.CREDIT_BALANCE_REFUND, date(2024, 2, 2), bd(40), bd(0), bd(0), bd(0), bd(0), bd(40), false));

        Money overpayment = underTest.calculateTotalOverpayment(loan);

        assertAmount("60", overpayment);
    }

    @Test
    void calculateTotalOverpayment_chargebackWithZeroPrincipalAndNoCreditAllocationReducesByOverpayment() {
        Loan loan = newLoan(bd(100));
        setInstallments(loan);
        setTransactions(loan, //
                repayment(loan, date(2024, 2, 1), bd(100), bd(100)), //
                tx(loan, LoanTransactionType.CHARGEBACK, date(2024, 2, 2), bd(25), bd(0), bd(0), bd(0), bd(0), bd(25), false));

        Money overpayment = underTest.calculateTotalOverpayment(loan);

        assertAmount("75", overpayment);
    }

    // ----- updateLoanSummaryDerivedFields -----

    @Test
    void updateLoanSummaryDerivedFields_zeroesSummaryWhenNotDisbursed() {
        Loan loan = newLoan(bd(100));
        LoanRepaymentScheduleInstallment installment = installment(loan, 1, date(2024, 1, 1), date(2024, 2, 1), bd(100), bd(0), bd(0),
                bd(0));
        setInstallments(loan, installment);
        setTransactions(loan);
        loan.setTotalOverpaid(bd(5));
        runFlushMode();

        underTest.updateLoanSummaryDerivedFields(loan);

        assertEquals(0, loan.getSummary().getTotalPrincipal().compareTo(BigDecimal.ZERO));
        assertNull(loan.getTotalOverpaid());
    }

    @Test
    void updateLoanSummaryDerivedFields_refreshesSummaryWhenDisbursed() {
        Loan loan = newLoan(bd(1000));
        LoanRepaymentScheduleInstallment installment = installment(loan, 1, date(2024, 1, 1), date(2024, 2, 1), bd(1000), bd(0), bd(0),
                bd(0));
        setInstallments(loan, installment);
        setTransactions(loan, tx(loan, LoanTransactionType.DISBURSEMENT, date(2024, 1, 1), bd(1000), bd(0), bd(0), bd(0), bd(0), bd(0),
                false));
        Money zero = Money.zero(USD);
        Mockito.when(capitalizedIncomeBalanceService.calculateCapitalizedIncome(loan)).thenReturn(zero);
        Mockito.when(capitalizedIncomeBalanceService.calculateCapitalizedIncomeAdjustment(loan)).thenReturn(zero);
        Mockito.when(loanTransactionRepository.calculateTotalRecoveryPaymentAmount(loan)).thenReturn(BigDecimal.ZERO);
        runFlushMode();

        underTest.updateLoanSummaryDerivedFields(loan);

        assertEquals(0, loan.getSummary().getTotalPrincipalDisbursed().compareTo(bd(1000)));
        assertEquals(0, loan.getSummary().getTotalPrincipalOutstanding().compareTo(bd(1000)));
        assertNull(loan.getTotalOverpaid());
    }

    @Test
    void refreshSummaryAndBalancesForDisbursedLoan_reconcilesFullyPaidChargesWhenSummaryOutstandingIsZero() {
        Loan loan = newLoan(bd(100));
        LoanRepaymentScheduleInstallment installment = installment(loan, 1, date(2024, 1, 1), date(2024, 2, 1), bd(100), bd(0), bd(0),
                bd(0));
        setInstallments(loan, installment);
        setTransactions(loan, tx(loan, LoanTransactionType.DISBURSEMENT, date(2024, 1, 1), bd(100), bd(0), bd(0), bd(0), bd(0), bd(0),
                false));
        LoanCharge feeCharge = charge(false, date(2024, 1, 15), bd(10));
        LoanCharge penaltyCharge = charge(true, date(2024, 1, 15), bd(5));
        setCharges(loan, feeCharge, penaltyCharge);
        Money zero = Money.zero(USD);
        Mockito.when(capitalizedIncomeBalanceService.calculateCapitalizedIncome(loan)).thenReturn(zero);
        Mockito.when(capitalizedIncomeBalanceService.calculateCapitalizedIncomeAdjustment(loan)).thenReturn(zero);
        Mockito.when(loanTransactionRepository.calculateTotalRecoveryPaymentAmount(loan)).thenReturn(BigDecimal.ZERO);

        underTest.refreshSummaryAndBalancesForDisbursedLoan(loan);

        assertTrue(feeCharge.isPaid());
        assertTrue(penaltyCharge.isPaid());
    }

    // ----- updateLoanOutstandingBalances -----

    @Test
    void updateLoanOutstandingBalances_computesRunningBalanceAcrossTransactionTypes() {
        Loan loan = newLoan(bd(1000));
        setInstallments(loan);
        LoanTransaction disbursement = tx(loan, LoanTransactionType.DISBURSEMENT, date(2024, 1, 1), bd(1000), bd(0), bd(0), bd(0), bd(0),
                bd(0), false);
        LoanTransaction repayment = repayment(loan, date(2024, 1, 2), bd(300), bd(300));
        LoanTransaction incomePosting = tx(loan, LoanTransactionType.INCOME_POSTING, date(2024, 1, 3), bd(50), bd(0), bd(0), bd(0), bd(0),
                bd(0), false);
        LoanTransaction capitalizedIncome = tx(loan, LoanTransactionType.CAPITALIZED_INCOME, date(2024, 1, 4), bd(100), bd(0), bd(0), bd(0),
                bd(0), bd(0), false);
        LoanTransaction creditBalanceRefund = tx(loan, LoanTransactionType.CREDIT_BALANCE_REFUND, date(2024, 1, 5), bd(50), bd(50), bd(0),
                bd(0), bd(0), bd(0), false);
        LoanTransaction chargebackOverpaid = tx(loan, LoanTransactionType.CHARGEBACK, date(2024, 1, 6), bd(20), bd(0), bd(0), bd(0), bd(0),
                bd(20), false);
        LoanTransaction reversed = tx(loan, LoanTransactionType.REPAYMENT, date(2024, 1, 7), bd(500), bd(500), bd(0), bd(0), bd(0), bd(0),
                true);
        LoanTransaction accrual = tx(loan, LoanTransactionType.ACCRUAL, date(2024, 1, 8), bd(15), bd(0), bd(15), bd(0), bd(0), bd(0), false);
        setTransactions(loan, disbursement, repayment, incomePosting, capitalizedIncome, creditBalanceRefund, chargebackOverpaid, reversed,
                accrual);

        underTest.updateLoanOutstandingBalances(loan);

        assertEquals(0, disbursement.getOutstandingLoanBalance().compareTo(bd(1000)));
        assertEquals(0, repayment.getOutstandingLoanBalance().compareTo(bd(700)));
        assertEquals(0, incomePosting.getOutstandingLoanBalance().compareTo(bd(750)));
        assertEquals(0, capitalizedIncome.getOutstandingLoanBalance().compareTo(bd(850)));
        assertEquals(0, creditBalanceRefund.getOutstandingLoanBalance().compareTo(bd(900)));
        assertEquals(0, chargebackOverpaid.getOutstandingLoanBalance().compareTo(bd(900)));
        assertNull(reversed.getOutstandingLoanBalance());
        assertNull(accrual.getOutstandingLoanBalance());
    }

    // ----- getReceivableInterest -----

    @Test
    void getReceivableInterest_accruesAndReducesInterestUpToTillDate() {
        Loan loan = newLoan(bd(1000));
        setInstallments(loan);
        setTransactions(loan, //
                tx(loan, LoanTransactionType.ACCRUAL, date(2024, 1, 1), bd(30), bd(0), bd(30), bd(0), bd(0), bd(0), false), //
                repayment(loan, date(2024, 1, 5), bd(10), bd(0), bd(10)), //
                tx(loan, LoanTransactionType.WAIVE_INTEREST, date(2024, 1, 6), bd(5), bd(0), bd(5), bd(0), bd(0), bd(0), false), //
                tx(loan, LoanTransactionType.ACCRUAL_ADJUSTMENT, date(2024, 1, 7), bd(3), bd(0), bd(3), bd(0), bd(0), bd(0), false), //
                tx(loan, LoanTransactionType.DISBURSEMENT, date(2024, 1, 2), bd(1000), bd(0), bd(0), bd(0), bd(0), bd(0), false), //
                tx(loan, LoanTransactionType.ACCRUAL, date(2024, 2, 1), bd(100), bd(0), bd(100), bd(0), bd(0), bd(0), false));

        Money receivable = underTest.getReceivableInterest(loan, date(2024, 1, 15));

        assertAmount("12", receivable);
    }

    @Test
    void getReceivableInterest_neverGoesNegative() {
        Loan loan = newLoan(bd(1000));
        setInstallments(loan);
        setTransactions(loan, //
                tx(loan, LoanTransactionType.ACCRUAL, date(2024, 1, 1), bd(10), bd(0), bd(10), bd(0), bd(0), bd(0), false), //
                repayment(loan, date(2024, 1, 5), bd(50), bd(0), bd(50)));

        Money receivable = underTest.getReceivableInterest(loan, date(2024, 1, 15));

        assertAmount("0", receivable);
    }

    // ----- fetchLoanForeclosureDetail -----

    @Test
    void fetchLoanForeclosureDetail_aggregatesOutstandingIncomeAndPrincipal() {
        Loan loan = newLoan(bd(1000));
        LoanSummary summary = loan.getSummary();
        ReflectionTestUtils.setField(summary, "totalPrincipalOutstanding", bd(500));

        LoanRepaymentScheduleInstallment past = installment(loan, 1, date(2024, 1, 1), date(2024, 2, 1), bd(100), bd(30), bd(10), bd(5));
        LoanRepaymentScheduleInstallment spanning = installment(loan, 2, date(2024, 3, 1), date(2024, 4, 1), bd(100), bd(30), bd(0), bd(0));
        LoanRepaymentScheduleInstallment future = installment(loan, 3, date(2024, 5, 1), date(2024, 6, 1), bd(100), bd(0), bd(0), bd(0));
        future.setInterestPaid(bd(7));
        future.setFeeChargesPaid(bd(3));
        future.setPenaltyChargesPaid(bd(2));
        setInstallments(loan, past, spanning, future);
        setTransactions(loan);

        LoanRepaymentScheduleInstallment foreclosure = underTest.fetchLoanForeclosureDetail(loan, date(2024, 3, 15));

        assertNotNull(foreclosure);
        assertEquals(BUSINESS_DATE, foreclosure.getDueDate());
        // interest = 30 (past outstanding) + 30 * 14/31 (spanning) = 43.55
        assertEquals(0, foreclosure.getInterestCharged(USD).getAmount().compareTo(bd("43.55")));
        assertEquals(0, foreclosure.getFeeChargesCharged(USD).getAmount().compareTo(bd(10)));
        assertEquals(0, foreclosure.getPenaltyChargesCharged(USD).getAmount().compareTo(bd(5)));
        // principal = 500 - paidFromFutureInstallments (7 + 3 + 2)
        assertEquals(0, foreclosure.getPrincipal(USD).getAmount().compareTo(bd(488)));
    }

    @Test
    void fetchLoanForeclosureDetail_includesFeeAndPenaltyChargesDueWithinSpanningPeriod() {
        Loan loan = newLoan(bd(1000));
        ReflectionTestUtils.setField(loan.getSummary(), "totalPrincipalOutstanding", bd(500));
        LoanRepaymentScheduleInstallment spanning = installment(loan, 1, date(2024, 3, 1), date(2024, 4, 1), bd(100), bd(30), bd(0),
                bd(0));
        spanning.setInterestPaid(bd(30));
        setInstallments(loan, spanning);
        setTransactions(loan);
        setCharges(loan, charge(false, date(2024, 3, 10), bd(8)), charge(true, date(2024, 3, 10), bd(4)));

        LoanRepaymentScheduleInstallment foreclosure = underTest.fetchLoanForeclosureDetail(loan, date(2024, 3, 15));

        assertEquals(0, foreclosure.getInterestCharged(USD).getAmount().compareTo(bd(0)));
        assertEquals(0, foreclosure.getFeeChargesCharged(USD).getAmount().compareTo(bd(8)));
        assertEquals(0, foreclosure.getPenaltyChargesCharged(USD).getAmount().compareTo(bd(4)));
        // interest fully accounted (paid 30 >= accrued 13.55) -> 30 - 13.55 = 16.45 added to future-paid
        assertEquals(0, foreclosure.getPrincipal(USD).getAmount().compareTo(bd("483.55")));
    }

    // ----- retrieveIncomeForOverlappingPeriod -----

    @Test
    void retrieveIncomeForOverlappingPeriod_returnsInstallmentChargesWhenPaymentDateOnDueDate() {
        Loan loan = newLoan(bd(1000));
        LoanRepaymentScheduleInstallment installment = installment(loan, 1, date(2024, 1, 1), date(2024, 2, 1), bd(100), bd(20), bd(5),
                bd(2));
        setInstallments(loan, installment);

        Money[] balances = underTest.retrieveIncomeForOverlappingPeriod(loan, date(2024, 2, 1));

        assertAmount("20", balances[0]);
        assertAmount("5", balances[1]);
        assertAmount("2", balances[2]);
    }

    @Test
    void retrieveIncomeForOverlappingPeriod_returnsZeroInterestForZeroInterestInstallment() {
        Loan loan = newLoan(bd(1000));
        LoanRepaymentScheduleInstallment installment = installment(loan, 1, date(2024, 1, 1), date(2024, 2, 1), bd(100), bd(0), bd(0),
                bd(0));
        setInstallments(loan, installment);

        Money[] balances = underTest.retrieveIncomeForOverlappingPeriod(loan, date(2024, 1, 15));

        assertAmount("0", balances[0]);
    }

    @Test
    void retrieveIncomeForOverlappingPeriod_returnsZeroesWhenPaymentDateOutsideAllPeriods() {
        Loan loan = newLoan(bd(1000));
        LoanRepaymentScheduleInstallment installment = installment(loan, 1, date(2024, 1, 1), date(2024, 2, 1), bd(100), bd(20), bd(5),
                bd(2));
        setInstallments(loan, installment);

        Money[] balances = underTest.retrieveIncomeForOverlappingPeriod(loan, date(2024, 3, 1));

        assertAmount("0", balances[0]);
        assertAmount("0", balances[1]);
        assertAmount("0", balances[2]);
    }

    // ----- helpers -----

    private void runFlushMode() {
        Mockito.doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(flushModeHandler).withFlushMode(Mockito.any(), Mockito.any(Runnable.class));
    }

    private Loan newLoan(final BigDecimal principal) {
        LoanProductRelatedDetail detail = newInstance(LoanProductRelatedDetail.class);
        detail.setCurrency(USD);
        detail.setPrincipal(principal);
        Loan loan = newInstance(Loan.class);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", detail);
        ReflectionTestUtils.setField(loan, "summary", LoanSummary.create(BigDecimal.ZERO));
        return loan;
    }

    private void setInstallments(final Loan loan, final LoanRepaymentScheduleInstallment... installments) {
        ReflectionTestUtils.setField(loan, "repaymentScheduleInstallments", new ArrayList<>(List.of(installments)));
    }

    private void setTransactions(final Loan loan, final LoanTransaction... transactions) {
        ReflectionTestUtils.setField(loan, "loanTransactions", new ArrayList<>(List.of(transactions)));
    }

    private void setCharges(final Loan loan, final LoanCharge... charges) {
        ReflectionTestUtils.setField(loan, "charges", new LinkedHashSet<>(List.of(charges)));
    }

    private LoanCharge charge(final boolean penalty, final LocalDate dueDate, final BigDecimal amount) {
        LoanCharge loanCharge = new LoanCharge();
        loanCharge.setAmount(amount);
        loanCharge.setAmountOutstanding(amount);
        loanCharge.setAmountPaid(BigDecimal.ZERO);
        loanCharge.setAmountWaived(BigDecimal.ZERO);
        loanCharge.setAmountWrittenOff(BigDecimal.ZERO);
        loanCharge.setChargeCalculation(ChargeCalculationType.FLAT.getValue());
        loanCharge.setChargeTime(ChargeTimeType.SPECIFIED_DUE_DATE.getValue());
        loanCharge.setPenaltyCharge(penalty);
        loanCharge.setDueDate(dueDate);
        return loanCharge;
    }

    private LoanRepaymentScheduleInstallment installment(final Loan loan, final int number, final LocalDate fromDate,
            final LocalDate dueDate, final BigDecimal principal, final BigDecimal interest, final BigDecimal fee, final BigDecimal penalty) {
        return new LoanRepaymentScheduleInstallment(loan, number, fromDate, dueDate, principal, interest, fee, penalty, false, null);
    }

    private LoanTransaction repayment(final Loan loan, final LocalDate date, final BigDecimal amount, final BigDecimal principalPortion) {
        return repayment(loan, date, amount, principalPortion, bd(0));
    }

    private LoanTransaction repayment(final Loan loan, final LocalDate date, final BigDecimal amount, final BigDecimal principalPortion,
            final BigDecimal interestPortion) {
        return tx(loan, LoanTransactionType.REPAYMENT, date, amount, principalPortion, interestPortion, bd(0), bd(0), bd(0), false);
    }

    private LoanTransaction tx(final Loan loan, final LoanTransactionType type, final LocalDate date, final BigDecimal amount,
            final BigDecimal principalPortion, final BigDecimal interestPortion, final BigDecimal feePortion,
            final BigDecimal penaltyPortion, final BigDecimal overPaymentPortion, final boolean reversed) {
        return new LoanTransaction(loan, null, type, date, amount, principalPortion, interestPortion, feePortion, penaltyPortion,
                overPaymentPortion, reversed, null, null);
    }

    private static void assertAmount(final String expected, final Money actual) {
        assertEquals(0, actual.getAmount().compareTo(new BigDecimal(expected)),
                () -> "expected " + expected + " but was " + actual.getAmount());
        assertEquals("USD", actual.getCurrency().getCode());
    }

    private static LocalDate date(final int year, final int month, final int day) {
        return LocalDate.of(year, month, day);
    }

    private static BigDecimal bd(final long value) {
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal bd(final String value) {
        return new BigDecimal(value);
    }

    private static <T> T newInstance(final Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
