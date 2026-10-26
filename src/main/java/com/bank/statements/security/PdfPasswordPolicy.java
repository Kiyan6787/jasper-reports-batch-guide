package com.bank.statements.security;

import com.bank.statements.model.AccountToRender;

@FunctionalInterface
public interface PdfPasswordPolicy {
    PdfProtection protectionFor(AccountToRender account);
}
