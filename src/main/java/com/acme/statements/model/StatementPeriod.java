package com.acme.statements.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

public record StatementPeriod(YearMonth month) {
    public StatementPeriod {
        Objects.requireNonNull(month, "month");
    }
    public LocalDate start() {
        return month.atDay(1);
    }
    public LocalDate endExclusive() {
        return month.plusMonths(1).atDay(1);
    }
    public LocalDate endInclusive() {
        return month.atEndOfMonth();
    }
    public StatementPeriod previous() {
        return new StatementPeriod(month.minusMonths(1));
    }
}
