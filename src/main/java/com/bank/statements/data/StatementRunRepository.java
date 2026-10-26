package com.bank.statements.data;

import com.bank.statements.model.StatementPeriod;
import com.bank.statements.model.StoredStatement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public class StatementRunRepository {

    private static final String CLAIM_SQL = """
        INSERT INTO statement_run (account_id, period_start, status, attempts,
        batch_id, claimed_at)
        VALUES (?, ?, 'RUNNING', 1, ?, now())
        ON CONFLICT (account_id, period_start) DO UPDATE
        SET status = 'RUNNING',
        attempts = statement_run.attempts + 1,
        batch_id = EXCLUDED.batch_id,
        claimed_at = now(),
        finished_at = NULL,
        error_code = NULL,
        error_message = NULL
        WHERE (statement_run.status = 'FAILED' AND statement_run.attempts < ?)
        OR (statement_run.status = 'RUNNING'
        AND statement_run.claimed_at < now() - make_interval(mins => ?))
        RETURNING attempts
        """;

    /** "AND batch_id = ?" fences out a stale owner that comes back to life. */
    private static final String SUCCEED_SQL = """
        UPDATE statement_run
        SET status = 'SUCCEEDED', finished_at = now(),
        opening_balance = ?, closing_balance = ?, txn_count = ?,
        page_count = ?, object_key = ?, sha256_hex = ?
        WHERE account_id = ? AND period_start = ? AND batch_id = ?
        AND status = 'RUNNING'
        """;

    private static final String FAIL_SQL = """
        UPDATE statement_run
        SET status = 'FAILED', finished_at = now(),
        error_code = ?, error_message = ?
        WHERE account_id = ? AND period_start = ? AND batch_id = ?
        AND status = 'RUNNING'
        """;

    private static final String CLOSING_SQL = """
        SELECT closing_balance
        FROM statement_run
        WHERE account_id = ? AND period_start = ? AND status = 'SUCCEEDED'
        """;

    private static final int MAX_ERROR_LENGTH = 2000;
    private final DataSource pool;
    public StatementRunRepository(DataSource pool) {
        this.pool = pool;
    }

    /** @return the attempt number if this batch now owns the statement */
    public OptionalInt claim(long accountId, StatementPeriod period, UUID batchId,
                             int maxRuns, Duration staleAfter) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(CLAIM_SQL)) {
            ps.setLong(1, accountId);
            ps.setObject(2, period.start());
            ps.setObject(3, batchId);
            ps.setInt(4, maxRuns);
            ps.setInt(5, Math.toIntExact(staleAfter.toMinutes()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? OptionalInt.of(rs.getInt(1)) : OptionalInt.empty();
            }
        }
    }

    public void markSucceeded(long accountId, StatementPeriod period, UUID batchId,
                              StoredStatement s) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(SUCCEED_SQL)) {
            ps.setBigDecimal(1, s.openingBalance());
            ps.setBigDecimal(2, s.closingBalance());
            ps.setInt(3, s.txnCount());
            ps.setInt(4, s.pageCount());
            ps.setString(5, s.objectKey());
            ps.setString(6, s.sha256Hex());
            ps.setLong(7, accountId);
            ps.setObject(8, period.start());
            ps.setObject(9, batchId);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("Lost the claim on account " + accountId
                        + " for " + period.month() + "; another batch took it over");
            }
        }
    }

    public boolean markFailed(long accountId, StatementPeriod period, UUID batchId,
                              String errorCode, String message) throws SQLException {
        String trimmed = message == null ? null
                : message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(FAIL_SQL)) {
            ps.setString(1, errorCode);
            ps.setString(2, trimmed);
            ps.setLong(3, accountId);
            ps.setObject(4, period.start());
            ps.setObject(5, batchId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Closing balance of a successfully issued statement, if there is one. */
    public Optional<BigDecimal> closingBalance(long accountId, StatementPeriod period)
            throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(CLOSING_SQL)) {
            ps.setLong(1, accountId);
            ps.setObject(2, period.start());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getBigDecimal(1)) : Optional.empty();
            }
        }
    }
}
