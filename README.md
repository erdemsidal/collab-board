# Spring Boot SaaS Boilerplate

A production-grade, opinionated starter for building SaaS products with Spring Boot 3 and Java 21. Comes with JWT authentication, refresh token rotation, PostgreSQL, Redis, Docker, and structured logging — so you can skip the boring infrastructure setup and start building your actual product.

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen?style=flat-square&logo=spring)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)
![Redis](https://img.shields.io/badge/Redis-7-red?style=flat-square&logo=redis)
![Docker](https://img.shields.io/badge/Docker-ready-blue?style=flat-square&logo=docker)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

---

## Why this exists

Every time I start a new SaaS idea, I waste the first week wiring up the same things: auth, refresh tokens, database migrations, exception handling, Docker setup, logging. It's busywork that has nothing to do with the product I actually want to build.

So I built this once, properly, and now every new project starts from here. Maybe it'll save you a week too.

This isn't a tutorial repo. It's a working foundation I use for real products. Every decision in here is made because I needed it, not because it looked good on a checklist.

---

## What's inside

- **JWT authentication** with refresh token rotation (Redis-backed, opaque UUID tokens)
- **PostgreSQL** with **Flyway** migrations (version-controlled schema)
- **Redis** for refresh token storage and caching
- **Spring Security 6** configured for stateless JWT (no sessions, no CSRF headache)
- **Docker Compose** for local dev — Postgres + Redis up in one command
- **Multi-stage Dockerfile** for production builds
- **Sentry** integration for error tracking
- **Structured JSON logging** (Logback + Logstash encoder) for production observability
- **Global exception handler** with RFC 7807 Problem Details format
- **GitHub Actions** CI pipeline
- **Feature-based package structure** (not the old `controller/service/repository` layout)

---

## Architecture at a glance

```
┌─────────────┐     HTTP      ┌──────────────────┐
│   Client    │ ────────────▶ │  AuthController  │
└─────────────┘   Bearer JWT  └────────┬─────────┘
                                       │
                              ┌────────▼─────────┐
                              │  JwtAuth Filter  │  ← validates token on every request
                              └────────┬─────────┘
                                       │
                              ┌────────▼─────────┐
                              │   AuthService    │
                              └────┬────────┬────┘
                                   │        │
                          ┌────────▼──┐  ┌──▼──────────────┐
                          │ Postgres  │  │ RefreshTokenSvc │
                          │  (users)  │  │   ↓ Redis       │
                          └───────────┘  └─────────────────┘
```

Short version: Stateless auth. JWT for access (15 min). Opaque refresh tokens in Redis (7 days, rotated on every use). Spring Security wires it together.

---

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | Java 21 (LTS) | Virtual threads + modern syntax (records, pattern matching) |
| Framework | Spring Boot 3.4 | Jakarta EE 10, native support, mature ecosystem |
| Security | Spring Security 6 + JJWT 0.12 | Industry standard for stateless auth |
| Database | PostgreSQL 16 | Boring is good. Battle-tested, JSON support, full-text search |
| Migrations | Flyway | SQL files in version control. No ORM-generated schema surprises |
| Cache & sessions | Redis 7 | Fast TTL-backed storage for refresh tokens and hot data |
| Build | Maven (via Maven Wrapper) | `./mvnw` works on any machine, no global install needed |
| Logging | Logback + Logstash Encoder | JSON logs in prod, human-readable in dev |
| Error tracking | Sentry | Free tier covers small SaaS easily |
| Containerization | Docker + Docker Compose | Same image runs locally and in production |
| CI | GitHub Actions | Built-in, free for public repos |

---

## Quick start

You need Java 21, Docker, and Git installed. That's it.

```bash
# 1. Clone
git clone https://github.com/erdemsidal/boilerplatespring.git
cd boilerplatespring

# 2. Copy environment template and fill in your secrets
cp .env.example .env
# Open .env and set JWT_SECRET (use a base64 string of at least 64 bytes)

# 3. Start PostgreSQL and Redis in Docker
docker compose up -d postgres redis

# 4. Run the app locally
./mvnw spring-boot:run
```

The app is now running on `http://localhost:8080`.

To generate a valid JWT secret on Windows PowerShell:
```powershell
[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

On Linux/Mac:
```bash
openssl rand -base64 64
```

---

## API endpoints

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Create a new user (no token returned — user must log in separately) |
| POST | `/api/auth/login` | Public | Returns access token (15 min) + refresh token (7 days) |
| POST | `/api/auth/refresh` | Public | Exchange refresh token for new access + refresh (token rotation) |
| POST | `/api/auth/logout` | Bearer | Invalidate refresh token in Redis |
| GET | `/api/users/me` | Bearer | Get current authenticated user |
| GET | `/actuator/health` | Public | Service health check |

Auth header format: `Authorization: Bearer <access_token>`

---

## Project structure

Feature-based, not layer-based. Each feature owns its controller, service, DTOs, and entities. This scales better than the old `controller/`, `service/`, `repository/` layout — when you delete a feature, you delete one folder, not surgery across six.

```
src/main/java/com/boilerplate/saas/
├── auth/                    # Authentication feature
│   ├── AuthController.java
│   ├── AuthService.java
│   ├── dto/                 # Request/response DTOs
│   └── entity/
├── user/                    # User management feature
│   ├── UserController.java
│   ├── UserService.java
│   ├── UserRepository.java
│   ├── dto/
│   └── entity/
├── security/                # Cross-cutting security primitives
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   ├── CustomUserDetailsService.java
│   ├── RefreshTokenService.java
│   └── SecurityConfig.java
├── common/                  # Shared across features
│   ├── audit/               # @CreatedDate, @LastModifiedDate
│   ├── dto/                 # ApiErrorResponse (RFC 7807)
│   └── exception/           # Global handler + custom exceptions
├── config/                  # Global beans (Redis, Jackson, OpenAPI)
└── health/                  # Health check endpoint
```

---

## Why these decisions?

This is the section I wish more boilerplates had. Here's the reasoning behind the non-obvious choices.

### Why JWT + opaque refresh tokens (not just JWT)?

JWTs can't be revoked once issued — only expired. That's fine for short-lived access tokens (15 min) but dangerous for long sessions. So:

- **Access token** = JWT, 15 min, stateless, fast
- **Refresh token** = opaque UUID, 7 days, stored in Redis, **revocable**

When you log out, we delete the refresh token from Redis. Even if someone steals the JWT, they only have 15 minutes to use it.

### Why rotate refresh tokens on every use?

Refresh token rotation makes stolen tokens self-destruct. Here's the scenario:
- Attacker steals refresh token `ABC` from victim
- Attacker uses it first → gets new token `XYZ`, `ABC` is deleted
- Victim later tries to use `ABC` → fails, they're logged out
- Victim notices, changes password, attacker's `XYZ` becomes useless

A stolen token gives you one shot, not a 7-day free pass.

### Why Redis for refresh tokens, not the database?

Refresh tokens are short-lived, write-heavy, and need automatic expiration. Redis gives you all three with a single `SET key value EX 604800` command. PostgreSQL would work, but you'd need a scheduled job to clean up expired rows. Redis just forgets them.

### Why Flyway over Hibernate's `ddl-auto`?

`ddl-auto=update` is a footgun in production. It silently drops columns, renames tables, and makes "what's the schema?" an unanswerable question. Flyway forces every change into a versioned SQL file. Boring, explicit, safe.

`hibernate.ddl-auto` is set to `validate` here — Hibernate only checks that the schema matches the entities, never modifies it.

### Why feature-based packages?

When you fix a bug in user signup, you touch `AuthController`, `AuthService`, `RegisterRequest`, `User`, `UserRepository`. In layer-based packaging, those are scattered across five directories. In feature-based, they're in two folders next to each other.

It also makes deletion safe — delete the `auth/` folder, the auth feature is gone. Try doing that in a layer-based project.

### Why stateless sessions?

Once you have JWT, sessions become redundant. They cost server memory and break horizontal scaling (sticky sessions, anyone?). `SessionCreationPolicy.STATELESS` tells Spring "don't bother — every request brings its own identity."

### Why CSRF disabled?

CSRF attacks rely on cookies being sent automatically by the browser. JWTs live in the `Authorization` header, which is **not** sent automatically — a malicious site can't trigger an authenticated request. CSRF protection on a JWT API does nothing useful and breaks all your POST endpoints.

### Why constructor injection?

Field injection (`@Autowired private SomeService x`) hides dependencies and breaks final fields. Constructor injection forces every dependency to be explicit, supports `final`, and works without Spring (great for testing).

### Why RFC 7807 Problem Details?

Returning errors as `{ "timestamp": ..., "status": 400, "errors": [...] }` is standardized in RFC 7807. Frontends and API clients can rely on a predictable shape across every endpoint and every project. The `GlobalExceptionHandler` enforces this format.

---

## Testing the auth flow with Postman

1. **Register** — `POST /api/auth/register` with `{ "firstName": "...", "lastName": "...", "email": "...", "password": "..." }` → 201 Created
2. **Login** — `POST /api/auth/login` → returns `accessToken` + `refreshToken`
3. **Protected request** — `GET /api/users/me` with `Authorization: Bearer <accessToken>` → 200 OK
4. **Refresh** — `POST /api/auth/refresh` with `{ "refreshToken": "..." }` → new tokens (old one is now invalid — try it again, you'll get 403)
5. **Logout** — `POST /api/auth/logout` with `{ "refreshToken": "..." }` and the Bearer header → refresh token deleted from Redis

---

## What's not included (yet)

This is a starter, not a finished product. Things I'll add as I need them:

- [ ] Email verification flow
- [ ] Password reset
- [ ] Rate limiting (per-IP and per-user)
- [ ] OAuth2 / social login
- [ ] Access token blacklist on logout (right now, access tokens stay valid until they expire — 15 min max)
- [ ] Integration tests for the auth flow
- [ ] API versioning (`/api/v1/...`)

If you're using this and one of these is blocking you, open an issue or PR.

---

## License

MIT. Use it, fork it, ship products with it. Attribution appreciated but not required.

---

## About

Built by [Erdem Sidal](https://github.com/erdemsidal) as the foundation for SaaS products I'm building.

If this saves you time, a ⭐ on the repo would mean a lot.
