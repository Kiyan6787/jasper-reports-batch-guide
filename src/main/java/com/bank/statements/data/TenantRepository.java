package com.bank.statements.data;

import com.bank.statements.model.Tenant;

import javax.sql.DataSource;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TenantRepository {

    private static final String SQL = """
        SELECT id, legal_name, primary_color, accent_color, logo_png,
        footer_text, locale_tag, currency_code
        FROM tenant
    """;
    private final DataSource pool;
    public TenantRepository(DataSource pool) {
        this.pool = pool;
    }

    /** Tenants are few and static for the duration of a batch: load them once. */
    public Map<String, Tenant> loadAll() throws SQLException {
        Map<String, Tenant> tenants = new HashMap<>();
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(SQL); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Tenant t = new Tenant(
                        rs.getString("id"),
                        rs.getString("legal_name"),
                        Color.decode(rs.getString("primary_color")),
                        Color.decode(rs.getString("accent_color")),
                        rs.getBytes("logo_png"),
                        rs.getString("footer_text"),
                        Locale.forLanguageTag(rs.getString("locale_tag")),
                        rs.getString("currency_code"));
                tenants.put(t.id(), t);
            }
        }
        return Map.copyOf(tenants);
    }
}
