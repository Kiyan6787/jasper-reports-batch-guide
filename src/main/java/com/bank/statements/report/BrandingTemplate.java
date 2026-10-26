package com.bank.statements.report;

import com.bank.statements.model.Tenant;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRSimpleTemplate;
import net.sf.jasperreports.engine.JRTemplate;
import net.sf.jasperreports.engine.design.JRDesignStyle;
import net.sf.jasperreports.engine.type.ModeEnum;

import java.awt.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class BrandingTemplate {
    private static final String FONT = "DejaVu Sans";
    private final ConcurrentHashMap<String, JRTemplate> cache = new ConcurrentHashMap<>();

    public JRTemplate forTenant(Tenant tenant) {
        return cache.computeIfAbsent(tenant.id(), id -> {
            try {
                return build(tenant);
            } catch (JRException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static JRTemplate build(Tenant t) throws JRException {
        JRSimpleTemplate template = new JRSimpleTemplate();

        template.addStyle(style("Brand.Title", s -> {
            s.setForecolor(t.primaryColor());
            s.setFontSize(18f);
            s.setBold(true);
        }));
        template.addStyle(style("Brand.BandFill", s -> {
            s.setMode(ModeEnum.OPAQUE);
            s.setBackcolor(t.primaryColor());
            s.setForecolor(Color.WHITE);
            s.setFontSize(7.5f);
            s.setBold(true);
        }));
        template.addStyle(style("Brand.Accent", s -> {
            s.setForecolor(t.accentColor());
            s.setFontSize(9f);
            s.setBold(true);
        }));
        template.addStyle(style("Brand.Rule", s -> s.setForecolor(t.accentColor())));
        return template;
    }

    private static JRDesignStyle style(String name, Consumer<JRDesignStyle> customizer) {
        JRDesignStyle s = new JRDesignStyle();
        s.setName(name);
        s.setFontName(FONT);
        customizer.accept(s);
        return s;
    }
}
