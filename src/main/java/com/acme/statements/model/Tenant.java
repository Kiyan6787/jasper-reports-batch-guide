package com.acme.statements.model;

import java.awt.*;
import java.util.Locale;
import java.util.Objects;

public record Tenant(
        String id,
        String legalName,
        Color primaryColor,
        Color accentColor,
        byte[] logoPng,
        String footerText,
        Locale locale,
        String currencyCode
) {

    public Tenant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(legalName, "legalName");
        Objects.requireNonNull(primaryColor, "primaryColor");
        Objects.requireNonNull(accentColor, "accentColor");
        Objects.requireNonNull(footerText, "footerText");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(currencyCode, "currencyCode");
        logoPng = logoPng == null ? null : logoPng.clone();
    }
    @Override
    public byte[] logoPng() {
        return logoPng == null ? null : logoPng.clone();
    }
}
