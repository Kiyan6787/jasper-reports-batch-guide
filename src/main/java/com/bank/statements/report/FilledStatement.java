package com.bank.statements.report;

import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;

public final class FilledStatement implements AutoCloseable {

    private final JasperPrint print;
    private final JRSwapFileVirtualizer virtualizer;

    FilledStatement(JasperPrint print, JRSwapFileVirtualizer virtualizer) {
        this.print = print;
        this.virtualizer = virtualizer;
    }

    public JasperPrint print() {
        return print;
    }

    public int pageCount() {
        return print.getPages().size();
    }

    @Override
    public void close() throws Exception {

    }
}
