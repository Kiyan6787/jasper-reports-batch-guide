package com.bank.statements.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class DataSourceFactory {

    private DataSourceFactory() {}

    public static HikariDataSource create(AppConfig cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("statements-db");
        hc.setJdbcUrl(cfg.string("db.url"));
        hc.setUsername(cfg.string("db.username"));
        hc.setPassword(cfg.string("db.password"));

        hc.setMaximumPoolSize(cfg.concurrency() + 4);
        hc.setMinimumIdle(2);
        hc.setConnectionTimeout(30_000);
        hc.setMaxLifetime(30 * 60_000);

        // Shows up in pg_stat_activity, which makes the batch easy to spot.
        hc.addDataSourceProperty("ApplicationName", "statement-batch");
        return new HikariDataSource(hc);
    }
}
