package com.acme.statements.data;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;

public class InMemoryTransactionDataSource extends RunningBalanceDataSource{
    private final Iterator<Txn> rows;
    public InMemoryTransactionDataSource(BigDecimal openingBalance, List<Txn> txns) {
        super(openingBalance);
        this.rows = List.copyOf(txns).iterator();
    }
    @Override
    protected Txn fetchNext() {
        return rows.hasNext() ? rows.next() : null;
    }
}
