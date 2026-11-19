-- Signing identity of the platform itself: it attests entries no workshop performed (OWNERSHIP_TRANSFER, RN-82).
-- Its Ed25519 key is generated at startup (the private key must be sealed with the KEK, which SQL cannot do).
INSERT INTO workshops.workshop (id, name, document_number, address, municipality, phone, status, verified_at, created_at)
VALUES ('00000000-0000-7000-8000-000000000001', 'Nítida (registro de plataforma)', 'N/A', 'N/A', 'N/A', 'N/A',
        'VERIFIED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
