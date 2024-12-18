-- V1__Create_Inventory_Tables.sql
-- Enable extension if not already done:
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE inventory
(
    inventory_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id         UUID      NOT NULL UNIQUE,
    available_quantity INTEGER   NOT NULL DEFAULT 0,
    reserved_quantity  INTEGER   NOT NULL DEFAULT 0,
    reorder_level      INTEGER   NOT NULL DEFAULT 10,
    reorder_quantity   INTEGER   NOT NULL DEFAULT 50,
    last_updated       TIMESTAMP NOT NULL DEFAULT NOW()
);