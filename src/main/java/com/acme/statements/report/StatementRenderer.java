package com.acme.statements.report;

import com.acme.statements.model.AccountToRender;
import com.acme.statements.model.Tenant;
import com.acme.statements.security.PdfProtection;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.util.JRSwapFile;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.pdf.SimplePdfExporterConfiguration;
import net.sf.jasperreports.renderers.SimpleDataRenderer;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TimeZone;

import static com.acme.statements.data.RunningBalanceDataSource.toUtcDate;

/**
 * Fill and export, kept as two steps so the caller can reconcile the filled
 * figures before anything is written or uploaded. Thread-safe: holds only
 * immutable, shareable objects; all per-statement state is local.
 */
public final class StatementRenderer {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final int SWAP_BLOCK_SIZE = 4096;
    private static final int SWAP_MIN_GROW_BLOCKS = 256;

    private final JasperReportsContext ctx;
    private final JasperReport report;
    private final BrandingTemplate branding;
    private final Path swapDir;
    private final int maxPagesInMemory;

    public StatementRenderer(JasperReportsContext ctx, JasperReport report, BrandingTemplate branding, Path swapDir, int maxPagesInMemory) {
        this.ctx = ctx;
        this.report = report;
        this.branding = branding;
        this.swapDir = swapDir;
        this.maxPagesInMemory = maxPagesInMemory;
    }

    public FilledStatement fill(StatementRequest request, JRDataSource rows) throws JRException {
        JRSwapFile swapFile = new JRSwapFile(ctx, swapDir.toString(), SWAP_BLOCK_SIZE, SWAP_MIN_GROW_BLOCKS);
        JRSwapFileVirtualizer virtualizer = new JRSwapFileVirtualizer(maxPagesInMemory, swapFile, true);
        boolean handedOver = false;

        try {
            Map<String, Object> params = parameters(request);
            params.put(JRParameter.REPORT_VIRTUALIZER, virtualizer);
            JasperPrint print = JasperFillManager.getInstance(ctx).fill(report,params,rows);
            virtualizer.setReadOnly(true);
            handedOver = true;
            return new FilledStatement(print, virtualizer);
        } finally {
            if (!handedOver) {
                virtualizer.cleanup();
            }
        }
    }

    public String exportPdf(FilledStatement filled, Path target, PdfProtection protection, StatementRequest request) throws JRException, IOException {
        MessageDigest sha256 = sha256();
        try (OutputStream file = Files.newOutputStream(target); OutputStream out = new BufferedOutputStream(new DigestOutputStream(file, sha256))) {
            JRPdfExporter exporter = new JRPdfExporter(ctx);
            exporter.setExporterInput(new SimpleExporterInput(filled.print()));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            exporter.setConfiguration(pdfConfiguration(protection, request));
            exporter.exportReport();
        }
        return HexFormat.of().formatHex(sha256.digest());
    }

    private Map<String, Object> parameters(StatementRequest req) {
        Tenant t = req.tenant();
        AccountToRender a = req.account();
        byte[] logo = t.logoPng();
        Map<String, Object> p = new HashMap<>();
        p.put(JRParameter.REPORT_LOCALE, t.locale()); // number and date formats
        p.put(JRParameter.REPORT_TIME_ZONE, UTC); // see toUtcDate()
        p.put("BRAND_TEMPLATE", branding.forTenant(t));
        p.put("TENANT_NAME", t.legalName());
        p.put("TENANT_LOGO", logo == null ? null : SimpleDataRenderer.getInstance(logo));
        p.put("FOOTER_TEXT", t.footerText());
        p.put("CURRENCY_CODE", t.currencyCode());
        p.put("CUSTOMER_NAME", a.customerName());
        p.put("ADDRESS_LINE_1", a.addressLine1());
        p.put("ADDRESS_LINE_2", a.addressLine2());
        p.put("CITY", a.city());
        p.put("POSTAL_CODE", a.postalCode());
        p.put("ACCOUNT_NO", a.accountNo());
        p.put("PRODUCT", a.product());
        p.put("PERIOD_START", toUtcDate(req.period().start()));
        p.put("PERIOD_END", toUtcDate(req.period().endInclusive()));
        p.put("STATEMENT_DATE", toUtcDate(req.statementDate()));
        p.put("OPENING_BALANCE", req.openingBalance());
        return p;
    }

    private static SimplePdfExporterConfiguration pdfConfiguration(PdfProtection protection, StatementRequest request) {
        SimplePdfExporterConfiguration c = new SimplePdfExporterConfiguration();
        c.setCompressed(true);
        c.setEncrypted(true);
        c.set128BitKey(true);
        c.setUserPassword(protection.userPassword());
        c.setOwnerPassword(protection.ownerPassword());
        c.setAllowedPermissionsHint("PRINTING");

        c.setMetadataTitle("Account statement " + request.period().month());
        c.setMetadataAuthor(request.tenant().legalName());
        c.setMetadataSubject("Account statement");
        c.setMetadataCreator("statement-batch");
        c.setDisplayMetadataTitle(true);
        return c;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }
}
