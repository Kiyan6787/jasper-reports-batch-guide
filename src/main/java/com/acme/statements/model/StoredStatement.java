package com.acme.statements.model;

import java.math.BigDecimal;

public record StoredStatement(
        String objectKey,
        String sha256Hex,
        int pageCount,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        int txnCount
) {
}
