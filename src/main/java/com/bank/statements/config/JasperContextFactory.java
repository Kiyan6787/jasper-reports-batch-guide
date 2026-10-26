package com.bank.statements.config;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;

public final class JasperContextFactory {

    /*
    * Setting them on the context makes them apply to every report and keeps the values in
    * configuration. A report can still override either one with a <property> of the same name.
    * */

    private JasperContextFactory() {};

    public static JasperReportsContext create(AppConfig cfg) {
        return create(Long.parseLong(cfg.string("report.fill.timeout.millis")), cfg.integer("report.max.pages"));
    }

    public static JasperReportsContext create(long fillTimeoutMillis, int maxPages) {
        SimpleJasperReportsContext ctx = new SimpleJasperReportsContext(DefaultJasperReportsContext.getInstance());

        ctx.setProperty("net.sf.jasperreports.governor.timeout.enabled", "true"); // Limits fill time
        ctx.setProperty("net.sf.jasperreports.governor.timeout", Long.toString(fillTimeoutMillis));
        ctx.setProperty("net.sf.jasperreports.governor.max.pages.enabled", "true"); // Limits page count and throws
        ctx.setProperty("net.sf.jasperreports.governor.max.pages", Integer.toString(maxPages));
        return ctx;
    }
}
