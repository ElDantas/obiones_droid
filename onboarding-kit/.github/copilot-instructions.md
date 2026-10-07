# Copilot instructions

Replace every section below with the facts for this repository. Keep it short; Copilot reads it on every task.

## Build & test commands

- Build: `./mvnw -B verify`
- Unit tests only: `./mvnw -B test`
- Run one test: `./mvnw -B test -Dtest=ClassNameTest`

## Architecture overview

- `src/main/java/.../api` — HTTP controllers; no business logic.
- `src/main/java/.../service` — business logic.
- `src/main/java/.../repository` — all database access.

## Conventions

- Database access only through `*Repository` classes.
- Money is `BigDecimal`, never `double`.
- New behaviour comes with a unit test in the matching `*Test` class.

## Do not touch

- `infra/**`, `**/migrations/**`, `.github/workflows/**`, secrets and `.env*` files.

## Definition of done

- All tests pass.
- Every acceptance criterion in the issue is covered by an automated test.
- No TODOs left in the change.
