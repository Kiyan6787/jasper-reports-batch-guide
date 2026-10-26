package com.acme.statements.security;

import com.acme.statements.model.AccountToRender;

@FunctionalInterface
public interface PdfPasswordPolicy {
    PdfProtection protectionFor(AccountToRender account);
}
