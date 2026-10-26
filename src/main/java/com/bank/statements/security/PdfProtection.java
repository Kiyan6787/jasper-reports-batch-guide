package com.bank.statements.security;

import java.util.Objects;

public record PdfProtection(
        String userPassword,
        String ownerPassword
) {
    public PdfProtection {
        Objects.requireNonNull(userPassword, "userPassword");
        Objects.requireNonNull(ownerPassword, "ownerPassword");
    }

    @Override
    public String toString() {
        return "PdfProtection[***]";
    }
}
