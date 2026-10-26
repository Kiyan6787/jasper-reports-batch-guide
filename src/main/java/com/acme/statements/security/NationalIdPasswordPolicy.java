package com.acme.statements.security;

import com.acme.statements.model.AccountToRender;

import java.security.SecureRandom;
import java.util.Base64;

public final class NationalIdPasswordPolicy implements PdfPasswordPolicy {

    private static final int MIN_DIGITS = 6;
    private final SecureRandom random = new SecureRandom();

    @Override
    public PdfProtection protectionFor(AccountToRender account) {
        String id = account.nationalId() == null ? "" : account.nationalId();
        String digits = id.replaceAll("\\D", "");
        if (digits.length() < MIN_DIGITS) {
            throw new IllegalArgumentException("Cannot derive a PDF password for account " + account.accountNo() + ": national ID is missing or too short");
        }
        byte[] owner = new byte[24];
        random.nextBytes(owner);
        return new PdfProtection(digits, Base64.getUrlEncoder().withoutPadding().encodeToString(owner));
    }
}
