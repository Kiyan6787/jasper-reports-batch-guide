package com.acme.statements.batch;

import net.sf.jasperreports.governors.MaxPagesGovernorException;
import net.sf.jasperreports.governors.TimeoutGovernorException;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.Set;

import static java.lang.reflect.Modifier.isTransient;

public class StatementFailure  extends Exception{
    private static final long serialVersionUID = 1L;

    public enum Kind { TRANSIENT, PERMANENT }

    private static final Set<String> TRANSIENT_SQL_STATES = Set.of("40001", "40P01", "57014");
    private static final int MAX_CAUSE_DEPTH = 32;
    private final Kind kind;
    private final String code;

    private StatementFailure(Kind kind, String code, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.code = code;
    }

    public static StatementFailure permanent(String code, String message) {
        return new StatementFailure(Kind.PERMANENT, code, message, null);
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    public static StatementFailure classify(Throwable t) {
        if (t instanceof StatementFailure f) {
            return f;
        }
        Throwable c = t;
        for (int depth = 0; c != null && depth < MAX_CAUSE_DEPTH; depth++, c = c.getCause()) {
            if (c instanceof TimeoutGovernorException) {
                return permanent(t, "FILL_TIMEOUT", "Report fill exceeded the governor timeout");
            }
            if (c instanceof MaxPagesGovernorException) {
                return permanent(t, "TOO_MANY_PAGES", "Report exceeded the maximum page count");
            }
            if (c instanceof InterruptedException || c instanceof InterruptedIOException
                    || c instanceof ClosedByInterruptException) {
                return permanent(t, "INTERRUPTED", "Cancelled by the watchdog or shutdown");
            }
//            if (c instanceof ObjectStoreException o) {
//                return o.retryable()
//                        ? new StatementFailure(Kind.TRANSIENT, "STORAGE_TRANSIENT",
//                        o.getMessage(), t)
//                        : permanent(t, "STORAGE_ERROR", o.getMessage());
//            }
            if (c instanceof SQLTransientException || c instanceof SQLRecoverableException
                    || c instanceof SQLException s && isTransient(s.getSQLState())) {
                return new StatementFailure(Kind.TRANSIENT, "DB_TRANSIENT", c.getMessage(), t);
            }
            if (c instanceof IllegalArgumentException) {
                return permanent(t, "INVALID_DATA", c.getMessage());
            }
        }
        return permanent(t, "RENDER_ERROR", String.valueOf(rootMessage(t)));
    }

    private static boolean isTransient(String sqlState) {
        return sqlState != null && (sqlState.startsWith("08") || TRANSIENT_SQL_STATES.contains(sqlState));
    }

    private static StatementFailure permanent(Throwable cause, String code, String message) {
        return new StatementFailure(Kind.PERMANENT, code, message, cause);
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        for (int i = 0; root.getCause() != null && i < MAX_CAUSE_DEPTH; i++) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
