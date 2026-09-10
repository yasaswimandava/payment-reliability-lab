# ADR-007: Package the operations console with the backend

- Status: Accepted
- Date: 2026-09-09

## Context

The React console and Spring Boot API have separate development toolchains, but
the lab needs one reproducible artifact that reviewers can build and run without
coordinating two production processes or configuring cross-origin access. The
runtime image should remain small, contain no build tools, and avoid running as
root.

## Decision

A multi-stage Docker build compiles the React application with Node, copies its
static output into Spring Boot resources, and packages one executable JAR with
Maven. A final JRE-only Alpine image runs that JAR as an unprivileged `paymentlab`
user. Spring Boot serves both the console and the `/api` and `/actuator` surfaces
from port `8080`.

Local source development remains split: Vite provides fast frontend feedback and
proxies backend calls to Spring Boot. The Compose `application` profile is the
integration and demonstration path; it builds the production image and connects
it to PostgreSQL, Redpanda, and Jaeger by service name.

The CI workflow verifies backend tests and coverage, frontend tests and coverage,
linting, the frontend build, Compose rendering, and the production image build.
Dependabot monitors all four dependency surfaces: Maven, npm, Docker, and GitHub
Actions.

## Consequences

- Reviewers can launch the complete system with one Compose command and open one
  URL.
- The release artifact uses same-origin API requests and needs no permissive CORS
  policy.
- Node, npm, Maven, source files, and test output are absent from the runtime
  image.
- Frontend and backend releases are coupled, which is appropriate for this lab's
  single-team demonstration but would limit independent deployment at larger
  organizational scale.
- The image does not include production orchestration, TLS termination, external
  secret injection, or horizontal autoscaling configuration.
