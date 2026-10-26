package com.bank.statements.batch;

import com.bank.statements.data.AccountRepository;
import com.bank.statements.data.StatementRunRepository;
import com.bank.statements.model.AccountToRender;
import com.bank.statements.model.StatementPeriod;
import com.bank.statements.model.StoredStatement;
import com.bank.statements.model.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Drives one period's run: pages through eligible accounts, renders them on
 * a bounded worker pool, enforces a per-statement deadline, retries
 * transient failures and records every outcome in statement_run.
 */
public final class StatementBatchJob {

    private static final Logger log = LoggerFactory.getLogger(StatementBatchJob.class);

    public record Settings(int concurrency,
                           Duration statementTimeout,
                           int transientRetries,
                           int maxRunsPerStatement,
                           Duration staleClaimAfter,
                           int accountPageSize) {
    }

    private enum Outcome { SUCCEEDED, FAILED, SKIPPED }

    private final AccountRepository accounts;
    private final StatementRunRepository runs;
    private final StatementGenerator generator;
    private final Map<String, Tenant> tenants;
    private final Settings settings;

    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final CountDownLatch finished = new CountDownLatch(1);

    public StatementBatchJob(AccountRepository accounts, StatementRunRepository runs,
                             StatementGenerator generator, Map<String, Tenant> tenants,
                             Settings settings) {
        this.accounts = accounts;
        this.runs = runs;
        this.generator = generator;
        this.tenants = tenants;
        this.settings = settings;
    }

    public BatchSummary run(StatementPeriod period) throws InterruptedException, SQLException {
        UUID batchId = UUID.randomUUID();
        Instant started = Instant.now();
        LongAdder succeeded = new LongAdder();
        LongAdder failed = new LongAdder();
        LongAdder skipped = new LongAdder();

        int workersCount = settings.concurrency();
        ExecutorService workers =
                Executors.newFixedThreadPool(workersCount, named("statement-worker-"));
        ScheduledExecutorService watchdog =
                Executors.newSingleThreadScheduledExecutor(named("statement-watchdog-"));

        // Backpressure: at most 2x workers statements are queued or running.
        // Without it the pager would load every eligible account into memory.
        Semaphore inFlight = new Semaphore(workersCount * 2);

        log.info("Batch {} started for {} with {} workers", batchId, period.month(), workersCount);
        try {
            long cursor = 0;
            pages:
            while (!stopRequested.get()) {
                List<AccountToRender> page =
                        accounts.page(period, cursor, settings.accountPageSize());
                if (page.isEmpty()) {
                    break;
                }
                for (AccountToRender account : page) {
                    if (stopRequested.get()) {
                        break pages;
                    }
                    inFlight.acquire();
                    try {
                        workers.execute(() -> {
                            try {
                                Outcome outcome = runWithDeadline(watchdog, account, period, batchId);
                                switch (outcome) {
                                    case SUCCEEDED -> succeeded.increment();
                                    case FAILED -> failed.increment();
                                    case SKIPPED -> skipped.increment();
                                }
                            } finally {
                                inFlight.release();
                            }
                        });
                    } catch (RejectedExecutionException e) {
                        inFlight.release();
                        throw e;
                    }
                }
                cursor = page.getLast().accountId();
            }
        } finally {
            workers.shutdown();
            long graceMillis = settings.statementTimeout().toMillis() * 2;
            if (!workers.awaitTermination(graceMillis, TimeUnit.MILLISECONDS)) {
                log.error("Workers did not stop within {} ms; interrupting", graceMillis);
                workers.shutdownNow();
            }
            watchdog.shutdownNow();
            finished.countDown();
        }

        BatchSummary summary = new BatchSummary(batchId, period.month(), succeeded.sum(),
                failed.sum(), skipped.sum(), Duration.between(started, Instant.now()),
                stopRequested.get());
        log.info("Batch finished: {}", summary);
        return summary;
    }

    /** Asks the pager to stop; in-flight statements are allowed to finish. */
    public void requestStop() {
        stopRequested.set(true);
    }

    public boolean awaitFinished(Duration timeout) throws InterruptedException {
        return finished.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * The deadline starts when the worker picks the statement up, not when it
     * is queued, so a long queue can never time out work that has not begun.
     */
    private Outcome runWithDeadline(ScheduledExecutorService watchdog, AccountToRender account,
                                    StatementPeriod period, UUID batchId) {
        Thread.interrupted(); // clear any stray interrupt left on this pooled thread
        Thread worker = Thread.currentThread();
        ScheduledFuture<?> alarm = watchdog.schedule(() -> {
            log.warn("Account {} exceeded {}; interrupting", account.accountNo(),
                    settings.statementTimeout());
            worker.interrupt();
        }, settings.statementTimeout().toMillis(), TimeUnit.MILLISECONDS);
        try {
            return processOne(account, period, batchId);
        } finally {
            alarm.cancel(false);
            Thread.interrupted(); // do not leak the interrupt into the next task
        }
    }

    private Outcome processOne(AccountToRender account, StatementPeriod period, UUID batchId) {
        MDC.put("batch", batchId.toString());
        MDC.put("tenant", account.tenantId());
        MDC.put("account", account.accountNo());
        try {
            OptionalInt claim;
            try {
                claim = runs.claim(account.accountId(), period, batchId,
                        settings.maxRunsPerStatement(), settings.staleClaimAfter());
            } catch (SQLException e) {
                log.error("Could not claim statement; it will be picked up next run", e);
                return Outcome.FAILED;
            }
            if (claim.isEmpty()) {
                log.debug("Skipped: already issued, owned by another batch, or out of runs");
                return Outcome.SKIPPED;
            }

            for (int attempt = 1; ; attempt++) {
                try {
                    Tenant tenant = tenants.get(account.tenantId());
                    if (tenant == null) {
                        throw StatementFailure.permanent("UNKNOWN_TENANT", account.tenantId());
                    }
                    StoredStatement stored = generator.generate(tenant, account, period);
                    runs.markSucceeded(account.accountId(), period, batchId, stored);
                    log.info("Issued {} ({} pages, {} transactions, run {})",
                            stored.objectKey(), stored.pageCount(), stored.txnCount(),
                            claim.getAsInt());
                    return Outcome.SUCCEEDED;
                } catch (Exception e) {
                    StatementFailure failure = StatementFailure.classify(e);
                    if (shouldRetry(failure, attempt)) {
                        long delay = backoffMillis(attempt);
                        log.warn("Transient failure {} on attempt {}; retrying in {} ms",
                                failure.code(), attempt, delay, e);
                        if (sleepInterrupted(delay)) {
                            recordFailure(account, period, batchId, StatementFailure.permanent(
                                    "INTERRUPTED", "Cancelled while waiting to retry"));
                            return Outcome.FAILED;
                        }
                        continue;
                    }
                    recordFailure(account, period, batchId, failure);
                    return Outcome.FAILED;
                }
            }
        } finally {
            MDC.clear();
        }
    }

    private boolean shouldRetry(StatementFailure failure, int attempt) {
        return failure.kind() == StatementFailure.Kind.TRANSIENT
                && attempt <= settings.transientRetries()
                && !Thread.currentThread().isInterrupted()
                && !stopRequested.get();
    }

    private void recordFailure(AccountToRender account, StatementPeriod period, UUID batchId,
                               StatementFailure failure) {
        // An interrupted thread cannot borrow a pooled connection, so clear the
        // flag while we write the failure, then restore it.
        boolean wasInterrupted = Thread.interrupted();
        try {
            if (!runs.markFailed(account.accountId(), period, batchId,
                    failure.code(), failure.getMessage())) {
                log.warn("Claim was lost before the failure could be recorded");
            }
            log.error("Statement failed [{}]: {}", failure.code(), failure.getMessage(),
                    failure.getCause() != null ? failure.getCause() : failure);
        } catch (SQLException e) {
            log.error("Could not record failure {}; the claim will go stale and be retried",
                    failure.code(), e);
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Exponential backoff with jitter: about 0.5s, 1s, 2s, ... capped at 32s. */
    private static long backoffMillis(int attempt) {
        long base = 500L << Math.min(attempt - 1, 6);
        return base + ThreadLocalRandom.current().nextLong(base / 2 + 1);
    }

    private static boolean sleepInterrupted(long millis) {
        try {
            Thread.sleep(millis);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private static ThreadFactory named(String prefix) {
        return Thread.ofPlatform().name(prefix, 1).factory();
    }
}
