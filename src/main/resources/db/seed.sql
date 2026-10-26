-- Local test data: one tenant, one customer, one account, 140 postings in September 2026.
INSERT INTO tenant (id, legal_name, primary_color, accent_color, logo_png,
                    footer_text, locale_tag, currency_code)
VALUES ('bank', 'Bank Ltd', '#0B3D91', '#E87722', NULL,
        'Bank Ltd is an authorised financial services provider. Queries: 0800 000 000.',
        'en-ZA', 'ZAR');
INSERT INTO customer (id, tenant_id, full_name, address_line_1, address_line_2,
                      city, postal_code, national_id)
VALUES (1, 'bank', 'Kiyan Ramjanum', '12 Pudding Street', 'Unit 4',
        'Johannesburg', '0181', '8001015009087.');
INSERT INTO account (id, customer_id, account_no, product, status, opened_on, closed_on)
VALUES (1001, 1, '62000001001', 'Everyday Cheque Account', 'ACTIVE', DATE '2024-01-15', NULL);
INSERT INTO ledger_txn (id, account_id, posted_on, description, reference, amount)
SELECT g,
       1001,
       DATE '2026-09-01' + (g % 30),
       CASE WHEN g % 7 = 0 THEN 'Salary - Bank Holdings' ELSE 'Card purchase #' || g END,
       'REF' || lpad(g::text, 6, '0'),
       CASE WHEN g % 7 = 0 THEN 18500.00 ELSE -((g % 13) + 1) * 37.25 END
FROM generate_series(1, 140) AS g;