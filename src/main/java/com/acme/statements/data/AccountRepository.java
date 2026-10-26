package com.acme.statements.data;

import com.acme.statements.model.AccountToRender;
import com.acme.statements.model.StatementPeriod;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class AccountRepository {

    private static final String PAGE_SQL = """
            SELECT a.id, a.account_no, a.product, c.tenant_id, c.full_name,
                c.address_line_1, c.address_line_2, c.city, c.postal_code,
                c.national_id
                FROM account a
                JOIN customer c ON c.id = a.customer_id
              WHERE a.id > ?
                AND a.opened_on < ?
                AND (a.closed_on IS NULL OR a.closed_on >= ?)
                AND NOT EXISTS (SELECT 1
                                FROM statement_run r
                                WHERE r.account_id = a.id
                                AND r.period_start = ?
                                AND r.status = 'SUCCEEDED')
            ORDER BY a.id
            LIMIT ?
            """;

    private final DataSource pool;
    public AccountRepository(DataSource pool) {
        this.pool = pool;
    }

    public List<AccountToRender> page(StatementPeriod period, long afterAccountId, int limit)
            throws SQLException {
        try (Connection c = pool.getConnection(); PreparedStatement ps = c.prepareStatement(PAGE_SQL)) {
            ps.setLong(1, afterAccountId);
            ps.setObject(2, period.endExclusive());
            ps.setObject(3, period.start());
            ps.setObject(4, period.start());
            ps.setInt(5, limit);
            List<AccountToRender> page = new ArrayList<>(limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    page.add(new AccountToRender(
                            rs.getLong("id"),
                            rs.getString("account_no"),
                            rs.getString("product"),
                            rs.getString("tenant_id"),
                            rs.getString("full_name"),
                            rs.getString("address_line_1"),
                            rs.getString("address_line_2"),
                            rs.getString("city"),
                            rs.getString("postal_code"),
                            rs.getString("national_id")));
                }
            }
            return page;
        }
    }
}
