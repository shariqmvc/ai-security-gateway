UPDATE PERSONAL_PAYMENT_INTENTS
SET base_amount = amount
WHERE base_amount = 0 AND credits > 0;
