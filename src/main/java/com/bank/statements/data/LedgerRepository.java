package com.bank.statements.data;

import com.bank.statements.model.StatementPeriod;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public final class LedgerRepository {

    private static final String BALANCE_BEFORE_SQL = """
            SELECT COALESCE(SUM(amount), 0)
              FROM ledger_txn
            WHERE account_id = ? AND posted_on < ?
        """;

    private static final String PERIOD_TOTALS_SQL = """
            SELECT COUNT(*), COALESCE(SUM(amount), 0)
                FROM ledger_txn
            WHERE account_id = ? AND posted_on >= ? AND posted_on < ?
        """;

    public record PeriodTotals(int count, BigDecimal net) {
    }
    private final DataSource pool;
    public LedgerRepository(DataSource pool) {
        this.pool = pool;
    }
    public BigDecimal balanceBefore(long accountId, LocalDate date) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(BALANCE_BEFORE_SQL)) {
            ps.setLong(1, accountId);
            ps.setObject(2, date);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }
    public PeriodTotals totals(long accountId, StatementPeriod period) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(PERIOD_TOTALS_SQL)) {
            ps.setLong(1, accountId);
            ps.setObject(2, period.start());
            ps.setObject(3, period.endExclusive());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new PeriodTotals(rs.getInt(1), rs.getBigDecimal(2));
            }
        }
    }
}
