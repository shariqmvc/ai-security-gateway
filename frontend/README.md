# AIRouter Personal v1 Frontend

React + Vite + TypeScript frontend for AIRouter Personal v1.

## Backend

Frozen backend baseline: `alroute-perosnal-v1`.

Frontend development branch: `airouter-personal-v1-frontend`.

## Local development

```bash
cd frontend
npm install
npm run dev
```

The default API origin is `http://localhost:8080`. Override with:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

## Implemented foundation

- Personal signup/login/session persistence
- Protected application shell
- Dashboard foundation
- Backend API client
- Model catalog adapter
- Usage summary adapter
- Credit wallet/ledger adapters
- API-key adapters
- Provider connection adapters
- Billing/security endpoint adapters
- RAG knowledge-base adapter
- Dedicated frontend branch

## Frozen backend contracts used

- `POST /public/auth/personal/signup`
- `POST /public/auth/personal/login`
- `POST /public/auth/personal/logout`
- `GET /api/personal/me`
- `GET /api/models`
- `POST /api/chat`
- `POST /api/chat/stream`
- `/api/personal/api-keys`
- `/api/personal/providers`
- `/api/personal/credits/*`
- `/api/personal/usage/*`
- `/api/personal/billing/intents`
- `/api/personal/security/assess`
- `/api/knowledge-bases/*`

The frontend intentionally does not move or mutate the frozen backend branch.
