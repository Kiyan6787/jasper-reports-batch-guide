package com.bank.statements.batch;

import com.bank.statements.data.JdbcTransactionDataSource;
import com.bank.statements.data.LedgerRepository;
import com.bank.statements.data.StatementRunRepository;
import com.bank.statements.model.AccountToRender;
import com.bank.statements.model.StatementPeriod;
import com.bank.statements.model.StoredStatement;
import com.bank.statements.model.Tenant;
import com.bank.statements.report.FilledStatement;
import com.bank.statements.report.StatementRenderer;
import com.bank.statements.report.StatementRequest;
import com.bank.statements.security.PdfPasswordPolicy;
import com.bank.statements.security.PdfProtection;
import com.bank.statements.storage.ObjectStore;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;

public final class StatementGenerator {

    private final DataSource pool;
    private final LedgerRepository ledger;
    private final StatementRunRepository runs;
    private final StatementRenderer renderer;
    private final PdfPasswordPolicy passwords;
    private final ObjectStore store;
    private final Path workDir;
    private final int fetchSize;
    private final int queryTimeoutSeconds;
    private final Clock clock;

    public StatementGenerator(DataSource pool, LedgerRepository ledger, StatementRunRepository runs, StatementRenderer renderer, PdfPasswordPolicy passwords, ObjectStore store, Path workDir, int fetchSize, int queryTimeoutSeconds, Clock clock) {
        this.pool = pool;
        this.ledger = ledger;
        this.runs = runs;
        this.renderer = renderer;
        this.passwords = passwords;
        this.store = store;
        this.workDir = workDir;
        this.fetchSize = fetchSize;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.clock = clock;
    }

    private record FillOutcome(FilledStatement filled, BigDecimal closing, int txnCount) {}

    public StoredStatement generate(Tenant tenant, AccountToRender account, StatementPeriod period) throws Exception {
        BigDecimal opening = resolveOpeningBalance(account, period);
        LedgerRepository.PeriodTotals expected = ledger.totals(account.accountId(), period);
        StatementRequest request = new StatementRequest(tenant, account, period, opening, LocalDate.now(clock));
        PdfProtection pdfProtection = passwords.protectionFor(account);

        Path pdf = Files.createTempFile(workDir, "stmt-", ".pdf");
        try {
            FillOutcome outcome = fill(request);
            String sha256;
            int pages;
            try (FilledStatement filled = outcome.filled()) {
                reconcile(opening, expected, outcome);
                ensureNotCancelled();
                sha256 = renderer.exportPdf(filled, pdf, pdfProtection, request);
                pages = filled.pageCount();
            }

            ensureNotCancelled();
            String key = objectKey(tenant, account, period);
            store.put(key, pdf, Map.of(
                    "sha256", sha256,
                    "period", period.month().toString(),
                    "pages", Integer.toString(pages)
            ));

            return new StoredStatement(key, sha256, pages, opening, outcome.closing(), outcome.txnCount());
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    private FillOutcome fill(StatementRequest request) throws Exception {
        try (JdbcTransactionDataSource rows = JdbcTransactionDataSource.open(pool, request.account().accountId(), request.period(), request.openingBalance(), fetchSize, queryTimeoutSeconds)) {
            FilledStatement filled = renderer.fill(request, rows);
            return new FillOutcome(filled, rows.closingBalance(), rows.rowCount());
        }
    }

    private BigDecimal resolveOpeningBalance(AccountToRender account, StatementPeriod period) throws SQLException, StatementFailure {
        BigDecimal fromLedger = ledger.balanceBefore(account.accountId(), period.start());
        Optional<BigDecimal> carried = runs.closingBalance(account.accountId(), period.previous());
        if (carried.isPresent() && carried.get().compareTo(fromLedger) != 0) {
            throw StatementFailure.permanent("OPENING_MISMATCH", "Previous statement closed at " + carried.get() + " but ledger shows " + fromLedger + " before " + period.start());
        }
        return fromLedger;
    }

    /** Proves the PDF shows exactly what the ledger holds, using a separate query. */
    private static void reconcile(BigDecimal opening, LedgerRepository.PeriodTotals expected, FillOutcome outcome) throws StatementFailure {
        BigDecimal expectedClosing = opening.add(expected.net());
        if (outcome.txnCount() != expected.count() || outcome.closing().compareTo(expectedClosing) != 0) {
            throw StatementFailure.permanent("RECONCILIATION_MISMATCH", "Rendered %d txns closing at %s; ledger has %d txns closing at %s"
                            .formatted(outcome.txnCount(), outcome.closing(),
                                    expected.count(), expectedClosing));
        }
    }

    private static void ensureNotCancelled() throws StatementFailure {
        if (Thread.currentThread().isInterrupted()) {
            throw StatementFailure.permanent("INTERRUPTED", "Cancelled by the watchdog or shutdown before upload");
        }
    }

    static String objectKey(Tenant tenant, AccountToRender account, StatementPeriod period) {
        YearMonth m = period.month();
        return "statements/%s/%d/%02d/%s.pdf".formatted(tenant.id(), m.getYear(), m.getMonthValue(), account.accountNo());
    }
}
