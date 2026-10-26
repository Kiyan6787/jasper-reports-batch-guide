package com.bank.statements.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class AppConfig {

    private static final String ENV_PREFIX = "STATEMENTS_";
    private final Properties props;

    private AppConfig(Properties props) {
        this.props = props;
    }

    public static AppConfig load() {
        Properties p = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in == null) {
                throw new IllegalStateException("application.properties is not on the classpath");
            }
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read application.properties", e);
        }

        String external = System.getProperty("config.file");
        if (external != null) {
            try (Reader r = Files.newBufferedReader(Path.of(external))) {
                p.load(r);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read config file " + external, e);
            }
        }

        System.getenv().forEach((name, value) -> {
            if (name.startsWith(ENV_PREFIX)) {
                String key = name.substring(ENV_PREFIX.length()).toLowerCase(Locale.ROOT).replace('_', '.');
                p.setProperty(key, value);
            }
        });
        return new AppConfig(p);
    }

    //For tests
    public static AppConfig of(Properties p) {
        return new AppConfig(p);
    }

    public String string(String key) {
        String v = props.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required configuration key: " + key);
        }
        return v.trim();
    }

    public int integer(String key) {
        String v = string(key);
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Key " + key + " is not an integer: " + v, e);
        }
    }

    /** batch.concurrency=0 means "one worker per CPU core". */
    public int concurrency() {
        int configured = integer("batch.concurrency");
        return configured > 0 ? configured : Runtime.getRuntime().availableProcessors();
    }
}
