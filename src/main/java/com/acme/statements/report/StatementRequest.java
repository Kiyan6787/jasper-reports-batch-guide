package com.acme.statements.report;

import com.acme.statements.model.AccountToRender;
import com.acme.statements.model.StatementPeriod;
import com.acme.statements.model.Tenant;

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
