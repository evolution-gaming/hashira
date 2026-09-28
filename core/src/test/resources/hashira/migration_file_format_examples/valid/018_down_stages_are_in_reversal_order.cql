-- description: splits payments into two tables
-- authoredAt: 1469630068000
-- up:

-- stage: 1
CREATE TABLE payments (id uuid PRIMARY KEY, amount decimal)

-- stage: 2
CREATE TABLE payments_by_account (
  account_id uuid,
  payment_id uuid,
  PRIMARY KEY (account_id, payment_id)
)

-- down:

-- stage: 1
DROP TABLE payments_by_account

-- stage: 2
DROP TABLE payments
