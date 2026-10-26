package com.acme.statements.data;

import com.acme.statements.model.StatementPeriod;
import net.sf.jasperreports.engine.JRException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class JdbcTransactionDataSource extends RunningBalanceDataSource{

    private static final String SQL = """
        SELECT posted_on, description, reference, amount
        FROM ledger_txn
        WHERE account_id = ? AND posted_on >= ? AND posted_on < ?
        ORDER BY posted_on, id
        """;
    private final Connection connection;
    private final PreparedStatement statement;
    private final ResultSet resultSet;
    private JdbcTransactionDataSource(BigDecimal openingBalance, Connection connection,
                                      PreparedStatement statement, ResultSet resultSet) {
        super(openingBalance);
        this.connection = connection;
        this.statement = statement;
        this.resultSet = resultSet;
    }

    public static JdbcTransactionDataSource open(DataSource pool, long accountId,
                                                 StatementPeriod period,
                                                 BigDecimal openingBalance,
                                                 int fetchSize,
                                                 int queryTimeoutSeconds) throws SQLException {
        Connection c = pool.getConnection();
        PreparedStatement ps = null;
        try {
            c.setAutoCommit(false);
            c.setReadOnly(true);
            ps = c.prepareStatement(SQL, ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(fetchSize);
            ps.setQueryTimeout(queryTimeoutSeconds);
            ps.setLong(1, accountId);
            ps.setObject(2, period.start());
            ps.setObject(3, period.endExclusive());
            ResultSet rs = ps.executeQuery();
            return new JdbcTransactionDataSource(openingBalance, c, ps, rs);
        } catch (SQLException | RuntimeException e) {
            SQLException cleanup = closeAll(null, ps, c);
            if (cleanup != null) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    @Override
    protected Txn fetchNext() throws SQLException {
        if (!resultSet.next()) {
            return null;
        }
        return new Txn(resultSet.getObject("posted_on", LocalDate.class),
                resultSet.getString("description"),
                resultSet.getString("reference"),
                resultSet.getBigDecimal("amount"));
    }
    @Override
    public void close() throws JRException {
        SQLException failure = closeStep(null, resultSet::close);
        failure = closeAll(failure, statement, connection);
        if (failure != null) {
            throw new JRException("Failed to release the transaction cursor", failure);
        }
    }
    /** Closes the statement, ends the read-only transaction, returns the connection. */
    private static SQLException closeAll(SQLException failure, PreparedStatement ps,
                                         Connection c) {
        if (ps != null) {
            failure = closeStep(failure, ps::close);
        }
        failure = closeStep(failure, c::rollback); // nothing to commit; frees the cursor
        return closeStep(failure, c::close); // back to the pool
    }
    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
    /** Runs every cleanup step even if an earlier one failed; keeps all errors. */
    private static SQLException closeStep(SQLException failure, SqlAction action) {
        try {
            action.run();
            return failure;
        } catch (SQLException e) {
            if (failure == null) {
                return e;
            }
            failure.addSuppressed(e);
            return failure;
        }
    }
}
