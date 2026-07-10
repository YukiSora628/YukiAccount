package com.yukisora.yukiaccount.domain.service

import com.yukisora.yukiaccount.domain.model.RecurringFrequency
import java.time.LocalDate
import java.time.YearMonth

internal fun LocalDate.nextRecurringDate(
    frequency: RecurringFrequency,
    startDate: LocalDate,
): LocalDate =
    when (frequency) {
        RecurringFrequency.DAILY -> plusDays(1)
        RecurringFrequency.WEEKLY -> plusWeeks(1)
        RecurringFrequency.MONTHLY -> {
            val nextMonth = YearMonth.from(this).plusMonths(1)
            nextMonth.atDay(minOf(startDate.dayOfMonth, nextMonth.lengthOfMonth()))
        }
    }
