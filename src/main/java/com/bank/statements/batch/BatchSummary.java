package com.bank.statements.batch;

import java.time.Duration;
import java.time.YearMonth;
import java.util.UUID;

public record BatchSummary(UUID batchId,
                           YearMonth month,
                           long succeeded,
                           long failed,
                           long skipped,
                           Duration elapsed,
                           boolean stoppedEarly) {

    public boolean clean() {
        return failed == 0 && !stoppedEarly;
    }
}
