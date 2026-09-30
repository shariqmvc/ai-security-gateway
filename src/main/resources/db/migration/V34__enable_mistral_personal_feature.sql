-- Mistral is a first-class Personal BYOK provider.
-- Enable it for existing Personal accounts while preserving the unique constraint.
INSERT INTO PERSONAL_ACCOUNT_FEATURES (personal_account_id, feature)
SELECT pa.id, 'MISTRAL'
FROM PERSONAL_ACCOUNTS pa
WHERE NOT EXISTS (
    SELECT 1
    FROM PERSONAL_ACCOUNT_FEATURES paf
    WHERE paf.personal_account_id = pa.id
      AND paf.feature = 'MISTRAL'
);
