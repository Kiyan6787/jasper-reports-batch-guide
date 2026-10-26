package com.bank.statements.report;

import com.bank.statements.model.AccountToRender;
import com.bank.statements.model.StatementPeriod;
import com.bank.statements.model.Tenant;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatementRequest(
        Tenant tenant,
        AccountToRender account,
        StatementPeriod period,
        BigDecimal openingBalance,
        LocalDate statementDate
) {
}
