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
package org.apache.fineract.portfolio.loanaccount.loanschedule.domain;

import static java.math.BigDecimal.ZERO;
import static java.util.Collections.emptyList;
import static org.apache.fineract.organisation.monetary.domain.MonetaryCurrency.fromApplicationCurrency;
import static org.apache.fineract.organisation.workingdays.domain.RepaymentRescheduleType.MOVE_TO_NEXT_WORKING_DAY;
import static org.apache.fineract.portfolio.common.domain.DayOfWeekType.INVALID;
import static org.apache.fineract.portfolio.common.domain.PeriodFrequencyType.DAYS;
import static org.apache.fineract.portfolio.common.domain.PeriodFrequencyType.MONTHS;
import static org.apache.fineract.portfolio.common.domain.PeriodFrequencyType.WEEKS;
import static org.apache.fineract.portfolio.common.domain.PeriodFrequencyType.YEARS;
import static org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType.CUMULATIVE;
import static org.apache.fineract.portfolio.loanproduct.domain.AmortizationMethod.EQUAL_PRINCIPAL;
import static org.apache.fineract.portfolio.loanproduct.domain.InterestCalculationPeriodMethod.SAME_AS_REPAYMENT_PERIOD;
import static org.apache.fineract.portfolio.loanproduct.domain.InterestMethod.FLAT;
import static org.apache.fineract.portfolio.loanproduct.domain.LoanPreCloseInterestCalculationStrategy.NONE;
import static org.apache.fineract.portfolio.loanproduct.domain.RepaymentStartDateType.DISBURSEMENT_DATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.holiday.domain.Holiday;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.workingdays.data.AdjustedDateDetailsDTO;
import org.apache.fineract.organisation.workingdays.domain.RepaymentRescheduleType;
import org.apache.fineract.organisation.workingdays.domain.WorkingDays;
import org.apache.fineract.portfolio.common.domain.DaysInMonthType;
import org.apache.fineract.portfolio.common.domain.DaysInYearType;
import org.apache.fineract.portfolio.common.domain.PeriodFrequencyType;
import org.apache.fineract.portfolio.loanaccount.data.HolidayDetailDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultScheduledDateGeneratorTest {

    private static MockedStatic<MoneyHelper> moneyHelperMock;
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_EVEN);

    private final DefaultScheduledDateGenerator underTest = new DefaultScheduledDateGenerator();

    @BeforeAll
    static void init() {
        moneyHelperMock = Mockito.mockStatic(MoneyHelper.class);
        moneyHelperMock.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(MC);
    }

    @BeforeEach
    void setUp() {
        FineractPlatformTenant tenant = new FineractPlatformTenant(1L, "default", "Default", "UTC", null);
        ThreadLocalContextUtil.setTenant(tenant);
    }

    @AfterEach
    void cleanUp() {
        ThreadLocalContextUtil.reset();
    }

    @AfterAll
    static void tearDown() {
        moneyHelperMock.close();
    }

    // ========== getRepaymentPeriodDate ==========

    @Test
    void getRepaymentPeriodDate_days_repaidEvery1() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate result = underTest.getRepaymentPeriodDate(DAYS, 1, start);
        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 2));
    }

    @Test
    void getRepaymentPeriodDate_days_repaidEvery3() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate result = underTest.getRepaymentPeriodDate(DAYS, 3, start);
        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 4));
    }

    @Test
    void getRepaymentPeriodDate_weeks_repaidEvery1() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate result = underTest.getRepaymentPeriodDate(WEEKS, 1, start);
        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 8));
    }

    @Test
    void getRepaymentPeriodDate_weeks_repaidEvery2() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate result = underTest.getRepaymentPeriodDate(WEEKS, 2, start);
        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void getRepaymentPeriodDate_months_repaidEvery1() {
        LocalDate start = LocalDate.of(2024, 1, 15);
        LocalDate result = underTest.getRepaymentPeriodDate(MONTHS, 1, start);
        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 15));
    }

    @Test
    void getRepaymentPeriodDate_months_repaidEvery3() {
        LocalDate start = LocalDate.of(2024, 1, 15);
        LocalDate result = underTest.getRepaymentPeriodDate(MONTHS, 3, start);
        assertThat(result).isEqualTo(LocalDate.of(2024, 4, 15));
    }

    @Test
    void getRepaymentPeriodDate_years_repaidEvery1() {
        LocalDate start = LocalDate.of(2024, 3, 1);
        LocalDate result = underTest.getRepaymentPeriodDate(YEARS, 1, start);
        assertThat(result).isEqualTo(LocalDate.of(2025, 3, 1));
    }

    @Test
    void getRepaymentPeriodDate_years_repaidEvery2() {
        LocalDate start = LocalDate.of(2024, 3, 1);
        LocalDate result = underTest.getRepaymentPeriodDate(YEARS, 2, start);
        assertThat(result).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void getRepaymentPeriodDate_invalid_returnsStartDate() {
        LocalDate start = LocalDate.of(2024, 3, 1);
        LocalDate result = underTest.getRepaymentPeriodDate(PeriodFrequencyType.INVALID, 1, start);
        assertThat(result).isEqualTo(start);
    }

    // ========== isDateFallsInSchedule ==========

    @Test
    void isDateFallsInSchedule_days_matching() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate date = LocalDate.of(2024, 1, 8); // 7 days, repaidEvery=7
        assertThat(underTest.isDateFallsInSchedule(DAYS, 7, start, date)).isTrue();
    }

    @Test
    void isDateFallsInSchedule_days_notMatching() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate date = LocalDate.of(2024, 1, 5); // 4 days, repaidEvery=7
        assertThat(underTest.isDateFallsInSchedule(DAYS, 7, start, date)).isFalse();
    }

    @Test
    void isDateFallsInSchedule_weeks_matching() {
        LocalDate start = LocalDate.of(2024, 1, 1); // Monday
        LocalDate date = LocalDate.of(2024, 1, 15); // 2 weeks later, Monday
        assertThat(underTest.isDateFallsInSchedule(WEEKS, 2, start, date)).isTrue();
    }

    @Test
    void isDateFallsInSchedule_weeks_notMatching() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate date = LocalDate.of(2024, 1, 9); // 1 week + 1 day (not 2-week boundary)
        assertThat(underTest.isDateFallsInSchedule(WEEKS, 2, start, date)).isFalse();
    }

    @Test
    void isDateFallsInSchedule_months_matching() {
        LocalDate start = LocalDate.of(2024, 1, 15);
        LocalDate date = LocalDate.of(2024, 4, 15); // 3 months, repaidEvery=3
        assertThat(underTest.isDateFallsInSchedule(MONTHS, 3, start, date)).isTrue();
    }

    @Test
    void isDateFallsInSchedule_months_notMatching() {
        LocalDate start = LocalDate.of(2024, 1, 15);
        LocalDate date = LocalDate.of(2024, 3, 15); // 2 months, repaidEvery=3
        assertThat(underTest.isDateFallsInSchedule(MONTHS, 3, start, date)).isFalse();
    }

    @Test
    void isDateFallsInSchedule_years_matching() {
        LocalDate start = LocalDate.of(2020, 6, 1);
        LocalDate date = LocalDate.of(2024, 6, 1); // 4 years, repaidEvery=2
        assertThat(underTest.isDateFallsInSchedule(YEARS, 2, start, date)).isTrue();
    }

    @Test
    void isDateFallsInSchedule_years_notMatching() {
        LocalDate start = LocalDate.of(2020, 6, 1);
        LocalDate date = LocalDate.of(2023, 6, 1); // 3 years, repaidEvery=2
        assertThat(underTest.isDateFallsInSchedule(YEARS, 2, start, date)).isFalse();
    }

    @Test
    void isDateFallsInSchedule_invalid_returnsFalse() {
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate date = LocalDate.of(2024, 2, 1);
        assertThat(underTest.isDateFallsInSchedule(PeriodFrequencyType.INVALID, 1, start, date)).isFalse();
    }

    // ========== generateNextRepaymentDate (3-arg) ==========

    @Test
    void generateNextRepaymentDate_firstRepaymentOverride() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        LocalDate firstRepaymentDate = LocalDate.of(2024, 2, 15);
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, disbursement, firstRepaymentDate, null);

        LocalDate result = underTest.generateNextRepaymentDate(disbursement, terms, true);
        assertThat(result).isEqualTo(firstRepaymentDate);
    }

    @Test
    void generateNextRepaymentDate_monthlyNormal() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, disbursement, null, null);

        LocalDate result = underTest.generateNextRepaymentDate(disbursement, terms, false);
        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 1));
    }

    @Test
    void generateNextRepaymentDate_weeklyRepaidEvery2() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        LoanApplicationTerms terms = buildTerms(WEEKS, 2, 4, disbursement, null, null);

        LocalDate result = underTest.generateNextRepaymentDate(disbursement, terms, false);
        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void generateNextRepaymentDate_dailyRepaidEvery14() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        LoanApplicationTerms terms = buildTerms(DAYS, 14, 4, disbursement, null, null);

        LocalDate result = underTest.generateNextRepaymentDate(disbursement, terms, false);
        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void generateNextRepaymentDate_yearlyRepaidEvery1() {
        LocalDate disbursement = LocalDate.of(2024, 3, 15);
        LoanApplicationTerms terms = buildTerms(YEARS, 1, 2, disbursement, null, null);

        LocalDate result = underTest.generateNextRepaymentDate(disbursement, terms, false);
        assertThat(result).isEqualTo(LocalDate.of(2025, 3, 15));
    }

    // ========== generateNextRepaymentDate (4-arg with periodNumber) ==========

    @Test
    void generateNextRepaymentDate_4arg_notLastPeriod() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, disbursement, null, null);

        LocalDate result = underTest.generateNextRepaymentDate(disbursement, terms, false, 2);
        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 1));
    }

    @Test
    void generateNextRepaymentDate_4arg_lastPeriodWithFixedLength() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, disbursement, null, 120);

        LocalDate result = underTest.generateNextRepaymentDate(disbursement, terms, false, 4);
        // fixedLength=120 days from disbursement => 2024-01-01 + 120 days = 2024-04-30
        LocalDate expectedMaxDate = terms.calculateMaxDateForFixedLength();
        assertThat(result).isEqualTo(expectedMaxDate);
    }

    // ========== generateRepaymentPeriods ==========

    @Test
    void generateRepaymentPeriods_monthlyLoan_4periods() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, disbursement, null, null);

        List<? extends LoanScheduleModelPeriod> periods = underTest.generateRepaymentPeriods(MC, disbursement, terms, holidayDTO);

        assertThat(periods).hasSize(4);
        assertThat(periods.get(0).periodFromDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(periods.get(0).periodDueDate()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(periods.get(1).periodFromDate()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(periods.get(1).periodDueDate()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(periods.get(2).periodFromDate()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(periods.get(2).periodDueDate()).isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(periods.get(3).periodFromDate()).isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(periods.get(3).periodDueDate()).isEqualTo(LocalDate.of(2024, 5, 1));
    }

    @Test
    void generateRepaymentPeriods_weeklyLoan_repaidEvery2() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(WEEKS, 2, 3, disbursement, null, null);

        List<? extends LoanScheduleModelPeriod> periods = underTest.generateRepaymentPeriods(MC, disbursement, terms, holidayDTO);

        assertThat(periods).hasSize(3);
        assertThat(periods.get(0).periodDueDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(periods.get(1).periodDueDate()).isEqualTo(LocalDate.of(2024, 1, 29));
        assertThat(periods.get(2).periodDueDate()).isEqualTo(LocalDate.of(2024, 2, 12));
    }

    @Test
    void generateRepaymentPeriods_dailyLoan() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(DAYS, 10, 3, disbursement, null, null);

        List<? extends LoanScheduleModelPeriod> periods = underTest.generateRepaymentPeriods(MC, disbursement, terms, holidayDTO);

        assertThat(periods).hasSize(3);
        assertThat(periods.get(0).periodDueDate()).isEqualTo(LocalDate.of(2024, 1, 11));
        assertThat(periods.get(1).periodDueDate()).isEqualTo(LocalDate.of(2024, 1, 21));
        assertThat(periods.get(2).periodDueDate()).isEqualTo(LocalDate.of(2024, 1, 31));
    }

    @Test
    void generateRepaymentPeriods_yearlyLoan() {
        LocalDate disbursement = LocalDate.of(2024, 6, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(YEARS, 1, 2, disbursement, null, null);

        List<? extends LoanScheduleModelPeriod> periods = underTest.generateRepaymentPeriods(MC, disbursement, terms, holidayDTO);

        assertThat(periods).hasSize(2);
        assertThat(periods.get(0).periodDueDate()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(periods.get(1).periodDueDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    // ========== adjustRepaymentDate ==========

    @Test
    void adjustRepaymentDate_workingDay_noChange() {
        // 2024-01-15 is Monday — a working day
        LocalDate date = LocalDate.of(2024, 1, 15);
        HolidayDetailDTO holidayDTO = createWeekdaysOnlyHolidayDTO();
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, date, null, null);

        AdjustedDateDetailsDTO result = underTest.adjustRepaymentDate(date, terms, holidayDTO);

        assertThat(result.getChangedScheduleDate()).isEqualTo(date);
    }

    @Test
    void adjustRepaymentDate_nonWorkingDay_movesToNextWorkingDay() {
        // 2024-01-13 is Saturday — a non-working day with weekdays-only config
        LocalDate saturday = LocalDate.of(2024, 1, 13);
        HolidayDetailDTO holidayDTO = createWeekdaysOnlyHolidayDTO();
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, LocalDate.of(2023, 12, 13), null, null);

        AdjustedDateDetailsDTO result = underTest.adjustRepaymentDate(saturday, terms, holidayDTO);

        // Should move to Monday 2024-01-15
        assertThat(result.getChangedScheduleDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void adjustRepaymentDate_holiday_reschedulesToSpecificDate() {
        // 2024-12-25 falls on a holiday that reschedules to 2024-12-27
        LocalDate christmasDay = LocalDate.of(2024, 12, 25);
        Holiday holiday = createHoliday("Christmas", LocalDate.of(2024, 12, 25), LocalDate.of(2024, 12, 25),
                LocalDate.of(2024, 12, 27));

        List<Holiday> holidays = new ArrayList<>();
        holidays.add(holiday);
        HolidayDetailDTO holidayDTO = new HolidayDetailDTO(true, holidays,
                new WorkingDays("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR,SA,SU", MOVE_TO_NEXT_WORKING_DAY.getValue(), false, false),
                false, false);

        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, LocalDate.of(2024, 11, 25), null, null);

        AdjustedDateDetailsDTO result = underTest.adjustRepaymentDate(christmasDay, terms, holidayDTO);

        assertThat(result.getChangedScheduleDate()).isEqualTo(LocalDate.of(2024, 12, 27));
    }

    // ========== getLastRepaymentDate ==========

    @Test
    void getLastRepaymentDate_monthlyLoan() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 6, disbursement, null, null);

        LocalDate lastDate = underTest.getLastRepaymentDate(terms, holidayDTO);

        assertThat(lastDate).isEqualTo(LocalDate.of(2024, 7, 1));
    }

    @Test
    void getLastRepaymentDate_weeklyLoan_repaidEvery2() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(WEEKS, 2, 4, disbursement, null, null);

        LocalDate lastDate = underTest.getLastRepaymentDate(terms, holidayDTO);

        assertThat(lastDate).isEqualTo(LocalDate.of(2024, 2, 26));
    }

    // ========== idealDisbursementDateBasedOnFirstRepaymentDate ==========

    @Test
    void idealDisbursementDate_months() {
        LocalDate firstRepayment = LocalDate.of(2024, 3, 15);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, LocalDate.of(2024, 2, 15), null, null);

        LocalDate result = underTest.idealDisbursementDateBasedOnFirstRepaymentDate(MONTHS, 1, firstRepayment, null, holidayDTO, terms);

        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 15));
    }

    @Test
    void idealDisbursementDate_weeks() {
        LocalDate firstRepayment = LocalDate.of(2024, 1, 15);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(WEEKS, 2, 4, LocalDate.of(2024, 1, 1), null, null);

        LocalDate result = underTest.idealDisbursementDateBasedOnFirstRepaymentDate(WEEKS, 2, firstRepayment, null, holidayDTO, terms);

        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void idealDisbursementDate_days() {
        LocalDate firstRepayment = LocalDate.of(2024, 1, 15);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(DAYS, 7, 4, LocalDate.of(2024, 1, 8), null, null);

        LocalDate result = underTest.idealDisbursementDateBasedOnFirstRepaymentDate(DAYS, 7, firstRepayment, null, holidayDTO, terms);

        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 8));
    }

    @Test
    void idealDisbursementDate_years() {
        LocalDate firstRepayment = LocalDate.of(2025, 6, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(YEARS, 1, 2, LocalDate.of(2024, 6, 1), null, null);

        LocalDate result = underTest.idealDisbursementDateBasedOnFirstRepaymentDate(YEARS, 1, firstRepayment, null, holidayDTO, terms);

        assertThat(result).isEqualTo(LocalDate.of(2024, 6, 1));
    }

    // ========== generateNextScheduleDateStartingFromDisburseDate ==========

    @Test
    void generateNextScheduleDateStartingFromDisburseDate_basic() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, disbursement, null, null);

        // lastRepaymentDate = disbursement, so next should be first repayment
        LocalDate result = underTest.generateNextScheduleDateStartingFromDisburseDate(disbursement, terms, holidayDTO);

        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 1));
    }

    @Test
    void generateNextScheduleDateStartingFromDisburseDate_afterSecondPeriod() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, disbursement, null, null);

        LocalDate result = underTest.generateNextScheduleDateStartingFromDisburseDate(LocalDate.of(2024, 2, 1), terms, holidayDTO);

        assertThat(result).isEqualTo(LocalDate.of(2024, 3, 1));
    }

    // ========== generateNextScheduleDateStartingFromDisburseDateOrRescheduleDate ==========

    @Test
    void generateNextScheduleDateStartingFromDisburseDateOrRescheduleDate_basic() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, disbursement, null, null);

        LocalDate result = underTest.generateNextScheduleDateStartingFromDisburseDateOrRescheduleDate(disbursement, terms, holidayDTO);

        assertThat(result).isEqualTo(LocalDate.of(2024, 2, 1));
    }

    // ========== Month-end edge case (seed date > 28) ==========

    @Test
    void generateNextRepaymentDate_monthEnd_adjustsForShorterMonth() {
        // Disbursement on Jan 1 but seed date is Jan 31 (month-end).
        // The seed date (not the disbursement date) must drive the month-end adjustment.
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        LocalDate seedDate = LocalDate.of(2024, 1, 31);
        LoanApplicationTerms terms = buildTermsWithSeedDate(MONTHS, 1, 4, disbursement, seedDate);

        // Verify the seed date routed correctly through repaymentsStartingFromDate
        assertThat(terms.getSeedDate()).as("seedDate must equal the explicit seed, not the disbursement date").isEqualTo(seedDate);
        assertThat(terms.getSeedDate()).isNotEqualTo(disbursement);

        // Generate next from Jan 31; Feb 2024 is a leap year with 29 days
        LocalDate result = underTest.generateNextRepaymentDate(LocalDate.of(2024, 1, 31), terms, false);
        // seed day 31 > Feb max 29, so adjustDate clamps to 29
        assertThat(result).as("month-end seed (day 31) should clamp to Feb's max (29)").isEqualTo(LocalDate.of(2024, 2, 29));

        // Contrast: generate a second period from Feb 29 to show the seed continues to matter.
        // With seed day 31: adjustDate clamps Mar 29 → Mar 31 (seed 31 > 28, date 29 >= 28).
        // Without seed (day 1): adjustDate is a no-op → stays Mar 29.
        LocalDate secondWithSeed = underTest.generateNextRepaymentDate(result, terms, false);
        assertThat(secondWithSeed).as("seed day 31 adjusts Mar to 31").isEqualTo(LocalDate.of(2024, 3, 31));

        LoanApplicationTerms termsWithoutSeed = buildTerms(MONTHS, 1, 4, disbursement, null, null);
        assertThat(termsWithoutSeed.getSeedDate().getDayOfMonth()).as("default seed uses disbursement day").isEqualTo(1);
        LocalDate secondWithoutSeed = underTest.generateNextRepaymentDate(result, termsWithoutSeed, false);
        assertThat(secondWithoutSeed).as("without month-end seed, second period differs").isNotEqualTo(secondWithSeed);
    }

    // ========== generateRepaymentPeriods with fixedLength ==========

    @Test
    void generateRepaymentPeriods_withFixedLength_overridesLastPeriod() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        // fixedLength=90 days => max date = 2024-01-01 + 90 = 2024-03-31
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 3, disbursement, null, 90);

        List<? extends LoanScheduleModelPeriod> periods = underTest.generateRepaymentPeriods(MC, disbursement, terms, holidayDTO);

        assertThat(periods).hasSize(3);
        // Last period should be capped to fixedLength date
        LocalDate expectedMaxDate = terms.calculateMaxDateForFixedLength();
        assertThat(periods.get(2).periodDueDate()).isEqualTo(expectedMaxDate);
    }

    // ========== getLastRepaymentDate with fixedLength ==========

    @Test
    void getLastRepaymentDate_withFixedLength_overridesLastDate() {
        LocalDate disbursement = LocalDate.of(2024, 1, 1);
        HolidayDetailDTO holidayDTO = createAllDaysWorkingHolidayDTO();
        // fixedLength=45 days => max date = 2024-01-01 + 45 = 2024-02-15
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 2, disbursement, null, 45);

        LocalDate lastDate = underTest.getLastRepaymentDate(terms, holidayDTO);

        LocalDate expectedMaxDate = terms.calculateMaxDateForFixedLength();
        assertThat(lastDate).isEqualTo(expectedMaxDate);
    }

    // ========== Holiday reschedule to next repayment date ==========

    @Test
    void adjustRepaymentDate_holiday_reschedulesToNextRepaymentDate() {
        // Holiday on 2024-02-01, reschedule type = RESCHEDULETONEXTREPAYMENTDATE (1)
        LocalDate repaymentDate = LocalDate.of(2024, 2, 1);
        Holiday holiday = new Holiday();
        holiday.setName("Test Holiday");
        holiday.setFromDate(LocalDate.of(2024, 2, 1));
        holiday.setToDate(LocalDate.of(2024, 2, 1));
        holiday.setRepaymentsRescheduledTo(null);
        holiday.setReschedulingType(1); // RESCHEDULETONEXTREPAYMENTDATE

        List<Holiday> holidays = new ArrayList<>();
        holidays.add(holiday);
        HolidayDetailDTO holidayDTO = new HolidayDetailDTO(true, holidays,
                new WorkingDays("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR,SA,SU", MOVE_TO_NEXT_WORKING_DAY.getValue(), false, false),
                false, false);

        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, LocalDate.of(2024, 1, 1), null, null);

        AdjustedDateDetailsDTO result = underTest.adjustRepaymentDate(repaymentDate, terms, holidayDTO);

        // Should be moved to next repayment date (2024-03-01)
        assertThat(result.getChangedScheduleDate()).isEqualTo(LocalDate.of(2024, 3, 1));
    }

    // ========== Non-working day with MOVE_TO_NEXT_REPAYMENT_MEETING_DAY ==========

    @Test
    void adjustRepaymentDate_nonWorkingDay_moveToNextRepaymentMeetingDay() {
        // 2024-01-13 is Saturday, config says move to next repayment meeting day (value=3)
        LocalDate saturday = LocalDate.of(2024, 1, 13);
        WorkingDays workingDays = new WorkingDays("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR",
                RepaymentRescheduleType.MOVE_TO_NEXT_REPAYMENT_MEETING_DAY.getValue(), false, false);
        HolidayDetailDTO holidayDTO = new HolidayDetailDTO(false, emptyList(), workingDays, false, false);

        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, LocalDate.of(2023, 12, 13), null, null);

        AdjustedDateDetailsDTO result = underTest.adjustRepaymentDate(saturday, terms, holidayDTO);

        // With MOVE_TO_NEXT_REPAYMENT_MEETING_DAY, should move to next repayment period date
        // Next repayment from 2024-01-13 + 1 month = 2024-02-13 (a Tuesday, which is a working day)
        assertThat(result.getChangedScheduleDate()).isEqualTo(LocalDate.of(2024, 2, 13));
    }

    // ========== generateNextRepaymentDateWhenHolidayApply ==========

    @Test
    void generateNextRepaymentDateWhenHolidayApply_noCalendar() {
        LocalDate lastRepaymentDate = LocalDate.of(2024, 1, 15);
        LoanApplicationTerms terms = buildTerms(MONTHS, 1, 4, LocalDate.of(2024, 1, 15), null, null);

        LocalDate result = underTest.generateNextRepaymentDateWhenHolidayApply(lastRepaymentDate, terms);

        // Without calendar, just adjusts date with seed and returns
        assertThat(result).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    // ========== Helper methods ==========

    private LoanApplicationTerms buildTerms(PeriodFrequencyType frequency, int repaidEvery, int numberOfRepayments,
            LocalDate disbursementDate, LocalDate calculatedRepaymentsStartingFrom, Integer fixedLength) {
        ApplicationCurrency dollarCurrency = new ApplicationCurrency("USD", "US Dollar", 2, 0, "currency.USD", "$");
        Money principalAmount = Money.of(fromApplicationCurrency(dollarCurrency), BigDecimal.valueOf(1000));

        LocalDate submittedOnDate = disbursementDate;
        return LoanApplicationTerms.assembleFrom(dollarCurrency.toData(), 1, frequency, numberOfRepayments, repaidEvery, frequency, null,
                INVALID, EQUAL_PRINCIPAL, FLAT, ZERO, frequency, ZERO, SAME_AS_REPAYMENT_PERIOD, false, principalAmount, disbursementDate,
                null, calculatedRepaymentsStartingFrom, null, null, null, null, null,
                Money.of(fromApplicationCurrency(dollarCurrency), ZERO), false, null, emptyList(), BigDecimal.valueOf(36_000L), null,
                DaysInMonthType.ACTUAL, DaysInYearType.ACTUAL, false, null, null, null, null, null, ZERO, null, NONE, null, ZERO,
                emptyList(), true, 0, false, createAllDaysWorkingHolidayDTO(), false, false, false, null, false, false, null, false,
                DISBURSEMENT_DATE, submittedOnDate, CUMULATIVE, LoanScheduleProcessingType.HORIZONTAL, fixedLength, false, null, null,
                false, null, false, null, null, null, false, null, null, null, false, false);
    }

    private LoanApplicationTerms buildTermsWithSeedDate(PeriodFrequencyType frequency, int repaidEvery, int numberOfRepayments,
            LocalDate disbursementDate, LocalDate seedDate) {
        ApplicationCurrency dollarCurrency = new ApplicationCurrency("USD", "US Dollar", 2, 0, "currency.USD", "$");
        Money principalAmount = Money.of(fromApplicationCurrency(dollarCurrency), BigDecimal.valueOf(1000));

        // Pass seedDate as repaymentsStartingFromDate so LoanApplicationTerms uses it as the seed date
        LocalDate submittedOnDate = disbursementDate;
        return LoanApplicationTerms.assembleFrom(dollarCurrency.toData(), 1, frequency, numberOfRepayments, repaidEvery, frequency, null,
                INVALID, EQUAL_PRINCIPAL, FLAT, ZERO, frequency, ZERO, SAME_AS_REPAYMENT_PERIOD, false, principalAmount, disbursementDate,
                seedDate, null, null, null, null, null, null, Money.of(fromApplicationCurrency(dollarCurrency), ZERO), false, null,
                emptyList(), BigDecimal.valueOf(36_000L), null, DaysInMonthType.ACTUAL, DaysInYearType.ACTUAL, false, null, null, null, null,
                null, ZERO, null, NONE, null, ZERO, emptyList(), true, 0, false, createAllDaysWorkingHolidayDTO(), false, false, false, null,
                false, false, null, false, DISBURSEMENT_DATE, submittedOnDate, CUMULATIVE, LoanScheduleProcessingType.HORIZONTAL, null,
                false, null, null, false, null, false, null, null, null, false, null, null, null, false, false);
    }

    private HolidayDetailDTO createAllDaysWorkingHolidayDTO() {
        return new HolidayDetailDTO(false, emptyList(),
                new WorkingDays("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR,SA,SU", MOVE_TO_NEXT_WORKING_DAY.getValue(), false, false),
                false, false);
    }

    private HolidayDetailDTO createWeekdaysOnlyHolidayDTO() {
        return new HolidayDetailDTO(false, emptyList(),
                new WorkingDays("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR", MOVE_TO_NEXT_WORKING_DAY.getValue(), false, false), false,
                false);
    }

    private Holiday createHoliday(String name, LocalDate fromDate, LocalDate toDate, LocalDate rescheduleTo) {
        Holiday holiday = new Holiday();
        holiday.setName(name);
        holiday.setFromDate(fromDate);
        holiday.setToDate(toDate);
        holiday.setRepaymentsRescheduledTo(rescheduleTo);
        // RescheduleType.RESCHEDULETOSPECIFICDATE = 2
        holiday.setReschedulingType(2);
        return holiday;
    }
}
