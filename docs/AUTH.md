# Multi-Tenant Login (DoDeal CRM)

The login screen collects **Client ID**, **Login ID**, **Password** and authenticates in two steps.

## Flow (implemented)

1. **Resolve client** — `GET {cmsBaseUrl}api/crmclients/{client_id}`
   (production `cmsBaseUrl = https://cmsapi.dodealcrm.com/`).
   Response gives `client_domain` + `status`. Login is refused if the client is missing or not `active`.
2. **Build tenant base URL** — `https://{client_domain}/api/`, persisted in `TenantStore`.
   Every later device request is routed to this tenant host by `TenantHostInterceptor`.
3. **Authenticate** — `POST {tenant}/login` with `{ "loginId": "...", "password": "..." }`.
   The returned token is stored (Keystore-backed) and attached to all device calls.

Enrollment is permanent: the tenant + token are reused across reboots; no logout, and a 401 refreshes silently.

## Where things live
- CMS host + tenant scheme: `config/EnvironmentConfig.kt` (`cmsBaseUrl`, `tenantScheme`).
- Client lookup: `data/api/cms/`.
- Tenant routing: `data/http/TenantHostInterceptor.kt`, `data/tenant/TenantStore.kt`.
- Orchestration: `domain/repository/AuthRepository.kt`.

## One thing to confirm with the backend
The auth spec fixed the **request** payload but not the **login response**. `TenantLoginResponse`
(`data/api/dto/TenantAuthDtos.kt`) already accepts common shapes — `token` / `access_token`,
optional `refresh_token`, `expires_in` / `expires_at`, `device_id` / `user_id` / `id`. If DoDeal's
`/login` returns different field names, adjust that one DTO (nothing else changes). If no expiry is
returned, the token is treated as valid for 12h and refreshed on demand.

## Local testing
STAGING points `cmsBaseUrl` and the tenant scheme at the mock (`http://<LAN-IP>:8080`). The mock
returns its own host as the tenant domain, so CMS lookup, `/login`, and `/device/*` all hit it.
Test login: Client ID `DODEAL-TEST`, Login ID `agent1`, Password `test1234`.
