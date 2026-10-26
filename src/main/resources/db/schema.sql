CREATE TABLE tenant (
    id TEXT PRIMARY KEY,
    legal_name TEXT NOT NULL,
    primary_color CHAR(7) NOT NULL,
    accent_color CHAR(7) NOT NULL,
    logo_png BYTEA,
    footer_text TEXT NOT NULL,
    locale_tag TEXT NOT NULL DEFAULT 'en-ZA',
    currency_code CHAR(3) NOT NULL DEFAULT 'ZAR'
);

CREATE TABLE customer (
    id BIGINT PRIMARY KEY,
    tenant_id TEXT NOT NULL REFERENCES tenant (id),
    full_name TEXT NOT NULL,
    address_line_1 TEXT,
    address_line_2 TEXT,
    city TEXT,
    postal_code TEXT,
    national_id TEXT NOT NULL -- used to derive the PDF open password
);

CREATE TABLE account (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customer (id),
    account_no TEXT NOT NULL UNIQUE,
    product TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'CLOSED', 'FROZEN')),
    opened_on DATE NOT NULL,
    closed_on DATE
);

-- The ledger: one row per posting. Positive = credit, negative = debit.
CREATE TABLE ledger_txn (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES account (id),
    posted_on DATE NOT NULL,
    description TEXT NOT NULL,
    reference TEXT,
    amount NUMERIC(19,2) NOT NULL
);
-- Serves both the streaming query (ORDER BY posted_on, id) and the totals query.
CREATE INDEX ledger_txn_account_date ON ledger_txn (account_id, posted_on, id);

CREATE TABLE statement_run (
    account_id BIGINT NOT NULL REFERENCES account (id),
    period_start DATE NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    attempts INT NOT NULL DEFAULT 0,
    batch_id UUID NOT NULL,
    claimed_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    opening_balance NUMERIC(19,2),
    closing_balance NUMERIC(19,2),
    txn_count INT,
    page_count INT,
    object_key TEXT,
    sha256_hex CHAR(64),
    error_code TEXT,
    error_message TEXT,
    PRIMARY KEY (account_id, period_start)
);
CREATE INDEX statement_run_period_status ON statement_run (period_start, status);