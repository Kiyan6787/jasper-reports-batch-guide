package com.acme.statements;

import com.acme.statements.batch.BatchSummary;
import com.acme.statements.batch.StatementBatchJob;
import com.acme.statements.batch.StatementGenerator;
import com.acme.statements.config.AppConfig;
import com.acme.statements.config.DataSourceFactory;
import com.acme.statements.config.JasperContextFactory;
import com.acme.statements.data.AccountRepository;
import com.acme.statements.data.LedgerRepository;
import com.acme.statements.data.StatementRunRepository;
import com.acme.statements.data.TenantRepository;
import com.acme.statements.model.StatementPeriod;
import com.acme.statements.model.Tenant;
import com.acme.statements.report.BrandingTemplate;
import com.acme.statements.report.CompiledReports;
import com.acme.statements.report.StatementRenderer;
import com.acme.statements.security.NationalIdPasswordPolicy;
import com.acme.statements.storage.LocalObjectStorage;
import com.acme.statements.storage.ObjectStore;
import com.zaxxer.hikari.HikariDataSource;
import net.sf.jasperreports.engine.JasperReportsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

/**
 * Entry point, usually started by a scheduler (cron, Kubernetes CronJob, ...).
 *   java -jar statement-batch.jar [yyyy-MM]
 * Without an argument it issues statements for the previous calendar month.
 * Exit codes: 0 = all issued, 2 = some failed or the run was stopped, 1 = aborted.
 */
public final class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true"); // servers have no display
        int exitCode;
        try {
            exitCode = run(args);
        } catch (Exception e) {
            log.error("Statement batch aborted", e);
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    static int run(String[] args) throws Exception {
        AppConfig cfg = AppConfig.load();
        ZoneId zone = ZoneId.of(cfg.string("app.zone"));
        YearMonth month = args.length > 0
                ? YearMonth.parse(args[0])
                : YearMonth.now(zone).minusMonths(1);
        StatementPeriod period = new StatementPeriod(month);

        Path workDir = Files.createDirectories(Path.of(cfg.string("report.work.dir")));
        Path swapDir = Files.createDirectories(Path.of(cfg.string("report.virtualizer.swap.dir")));

        try (HikariDataSource pool = DataSourceFactory.create(cfg);
             ObjectStore store = createStore(cfg)) {

            JasperReportsContext jasper = JasperContextFactory.create(cfg);
            CompiledReports reports = new CompiledReports(jasper);
            StatementRenderer renderer = new StatementRenderer(jasper, reports.statement(),
                    new BrandingTemplate(), swapDir,
                    cfg.integer("report.virtualizer.max.pages.in.memory"));

            LedgerRepository ledger = new LedgerRepository(pool);
            StatementRunRepository runs = new StatementRunRepository(pool);
            StatementGenerator generator = new StatementGenerator(pool, ledger, runs, renderer,
                    new NationalIdPasswordPolicy(), store, workDir,
                    cfg.integer("db.fetch.size"), cfg.integer("db.query.timeout.seconds"),
                    Clock.system(zone));

            Map<String, Tenant> tenants = new TenantRepository(pool).loadAll();
            StatementBatchJob job = new StatementBatchJob(new AccountRepository(pool), runs,
                    generator, tenants, settings(cfg));

            // SIGTERM (deploys, node drains): stop taking new accounts, let the
            // in-flight statements finish, then exit. Nothing is half-issued.
            Duration grace = Duration.ofSeconds(cfg.integer("batch.shutdown.grace.seconds"));
            Thread hook = new Thread(() -> {
                log.warn("Shutdown requested; finishing in-flight statements");
                job.requestStop();
                try {
                    job.awaitFinished(grace);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "shutdown-hook");
            Runtime.getRuntime().addShutdownHook(hook);

            BatchSummary summary = job.run(period);

            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException alreadyShuttingDown) {
                // JVM is already exiting; the hook is running. Nothing to do.
            }
            return summary.clean() ? 0 : 2;
        }
    }

    private static StatementBatchJob.Settings settings(AppConfig cfg) {
        return new StatementBatchJob.Settings(
                cfg.concurrency(),
                Duration.ofSeconds(cfg.integer("batch.statement.timeout.seconds")),
                cfg.integer("batch.transient.retries"),
                cfg.integer("batch.max.runs.per.statement"),
                Duration.ofMinutes(cfg.integer("batch.stale.claim.minutes")),
                cfg.integer("batch.account.page.size"));
    }

    private static ObjectStore createStore(AppConfig cfg) {
        return switch (cfg.string("storage.type")) {
            // TODO: wire in S3ObjectStore (guide chapter 14) once it is added to the project.
            case "s3" -> throw new IllegalStateException(
                    "storage.type=s3 is not available yet: S3ObjectStore has not been implemented");
            case "local" -> new LocalObjectStorage(Path.of(cfg.string("storage.local.dir")));
            default -> throw new IllegalStateException(
                    "storage.type must be s3 or local, was " + cfg.string("storage.type"));
        };
    }
}
