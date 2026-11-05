# Telecom Platform – Phases 1, 

Spring Boot 3 (Java 21) + MySQL 8 backend, and two React + Vite + MUI apps:
an **operator console** (staff) and a **customer portal**, built separately from one codebase.

```
backend/    Spring Boot API, Flyway migration, tests
frontend/   React apps (operator on :5173, customer on :5174)
docker-compose.yml   MySQL for local development
```

## Run locally
Requirements: JDK 21, Maven 3.9+, Node 20+, Docker.

```bash
docker compose up -d                                   # MySQL on 127.0.0.1:3306
cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run     # API on :8080
cd frontend && npm install
npm run dev:operator      # http://localhost:5173
npm run dev:customer      # http://localhost:5174
```
The `dev` profile creates sample data (see `DevDataSeeder`) and prints one-time codes in the
backend console instead of sending email.

Sample accounts (all use email OTP, then set up an authenticator app on first login):
- Operator console: `super@platform.test` (Super Admin), `admin@netbridge.test`, `planner@netbridge.test`, `engineer@netbridge.test`, `admin@skylink.test`
- Customer portal: `anita@example.com`, `admin@orbit.test`, `billing@orbit.test`

## Sign-in flow
1. `POST /api/auth/{pool}/otp/request` – `pool` is `staff` or `customer`.
2. `POST /api/auth/{pool}/otp/verify` – returns a short-lived challenge.
3. `POST /api/auth/mfa/setup` then `/api/auth/mfa/confirm` (first login) – authenticator app (TOTP).
4. Access token in the response body; refresh token in an HttpOnly cookie.
5. `POST /api/auth/{pool}/refresh`, `POST /api/auth/{pool}/logout`, `GET /api/me`.

Dashboards: `GET /api/dashboard/platform` (Super Admin), `/operator` (operator staff), `/customer` (customers).
Roles and tenant scope come from the verified token, never from request parameters.

## Configuration
Outside the `dev` profile the app refuses to start without `JWT_SECRET`, `OTP_PEPPER` and
`TOTP_ENCRYPTION_KEY` (see `.env.example`). Never reuse the dev values.

ds them.
