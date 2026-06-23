CREATE TABLE IF NOT EXISTS financial_product (
    product_id BIGINT PRIMARY KEY,
    product_name VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS etf_detail (
    etf_detail_id BIGINT PRIMARY KEY,
    product_id BIGINT,
    distribution_interval_months INT
);

CREATE TABLE IF NOT EXISTS dividend_history (
    dist_id BIGINT PRIMARY KEY,
    product_id BIGINT,
    payment_date DATE,
    amount_per_unit DECIMAL(15, 2)
);
