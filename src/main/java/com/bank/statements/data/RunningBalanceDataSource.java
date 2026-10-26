package com.bank.statements.data;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Objects;

public abstract class RunningBalanceDataSource implements JRDataSource, AutoCloseable {

    public record Txn(LocalDate postedOn, String description, String reference, BigDecimal amount) {
        public Txn {
            Objects.requireNonNull(postedOn, "postedOn");
            Objects.requireNonNull(amount, "amount");
        }
    }

    private final BigDecimal openingBalance;
    private BigDecimal runningBalance;
    private BigDecimal balanceBefore;
    private BigDecimal totalCredits = BigDecimal.ZERO;
    private BigDecimal totalDebits = BigDecimal.ZERO;
    private int rowCount;
    private Txn current;

    protected RunningBalanceDataSource(BigDecimal openingBalance) {
        this.openingBalance = Objects.requireNonNull(openingBalance, "openingBalance");
        this.runningBalance = openingBalance;
    }

    protected abstract Txn fetchNext() throws Exception;

    @Override
    public final boolean next() throws JRException {
        Txn txn;
        try {
            txn = fetchNext();
        } catch (JRException e) {
            throw e;
        } catch (Exception e) {
            throw new JRException("Failed to read next transaction", e);
        }
        if (txn == null) {
            return false;
        }
        current = txn;
        balanceBefore = runningBalance;
        runningBalance = runningBalance.add(txn.amount());
        if (txn.amount().signum() >= 0) {
            totalCredits = totalCredits.add(txn.amount());
        } else {
            totalDebits = totalDebits.add(txn.amount().negate());
        }
        rowCount++;
        return true;
    }

    @Override
    public Object getFieldValue(JRField field) throws JRException {
        Txn t = current;
        if (t == null) {
            throw new JRException("getFieldValue() called before next()");
        }
        return switch (field.getName()) {
            case "postedOn" -> toUtcDate(t.postedOn());
            case "description" -> t.description();
            case "reference" -> t.reference();
            case "debit" -> t.amount().signum() < 0 ? t.amount().negate() : null;
            case "credit" -> t.amount().signum() >= 0 ? t.amount() : null;
            case "balanceBefore" -> balanceBefore;
            case "balance" -> runningBalance;
            default -> throw new JRException("Unknown report field: " + field.getName());
        };
    }

    /**
     * A LocalDate has no time zone; java.util.Date does. We pin dates to UTC
     * midnight and fill the report with REPORT_TIME_ZONE = UTC, so a date can
     * never shift by a day because of the server's default zone.
     */
    public static Date toUtcDate(LocalDate date) {
        return date == null ? null : Date.from(date.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    public BigDecimal openingBalance() {
        return openingBalance;
    }

    public BigDecimal closingBalance() {
        return runningBalance;
    }

    public BigDecimal totalCredits() {
        return totalCredits;
    }

    public BigDecimal totalDebits() {
        return totalDebits;
    }

    public int rowCount() {
        return rowCount;
    }

    @Override
    public void close() throws JRException {
        // Nothing to release by default.
    }
}
