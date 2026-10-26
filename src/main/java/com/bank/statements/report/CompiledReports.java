package com.bank.statements.report;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.util.JRLoader;

import java.io.IOException;
import java.io.InputStream;

public final class CompiledReports {

    public static final String STATEMENT = "/reports/statement.jasper";
    private final JasperReport statement;

    public CompiledReports(JasperReportsContext ctx) {
        this.statement = load(ctx, STATEMENT);
    }

    public JasperReport statement() {
        return statement;
    }

    private static JasperReport load(JasperReportsContext ctx, String resource) {
        try (InputStream in = CompiledReports.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the classpath. The " + "jasperreports-maven-plugin compiles it during process-resources.");
            }
            return (JasperReport) JRLoader.loadObject(ctx, in);
        } catch (IOException | JRException e) {
            throw new IllegalStateException("Cannot load compiled report " + resource, e);
        }
    }
}
