-- Reservations created before invocation tracking was introduced have unknown provider state.
-- Treat existing RESERVED rows conservatively so recovery never releases a potentially in-flight request.
UPDATE PERSONAL_CREDIT_RESERVATIONS
SET provider_invocation_started = TRUE
WHERE status = 'RESERVED'
  AND provider_invocation_started = FALSE;
