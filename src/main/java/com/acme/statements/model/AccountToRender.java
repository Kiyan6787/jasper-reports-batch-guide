package com.acme.statements.model;

public record AccountToRender(
        long accountId,
        String accountNo,
        String product,
        String tenantId,
        String customerName,
        String addressLine1,
        String addressLine2,
        String city,
        String postalCode,
        String nationalId
) {

    @Override
    public String toString() {
        return "AccountToRender[accountId=" + accountId + ", accountNo=" + accountNo + ", tenantId=" + tenantId + "]";
    }
}
