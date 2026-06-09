# 📚 Library Management System

A minimal, **production-style** REST API for managing books — built to learn the Java/Spring backend
stack from first principles. It demonstrates the full path from an HTTP request down to a relational
database, with **Redis caching** layered in front of reads so you can clearly observe cache **hits** vs
**misses**.

> This is a **learning project**: the code is deliberately small, but it is heavily documented
> (see [`docs/`](docs/)) to explain *why* every piece exists and *how* it works internally.

---

## ✨ Highlights

- **Clean layered architecture** — `controller → service → repository → entity`, with a `config` layer.
- **Spring Data JPA + Hibernate** over **PostgreSQL** — full CRUD with almost no boilerplate.
- **Redis cache-aside** on `GET /books/{id}` — with logs that make cache **HIT/MISS** visible.
- **Docker Compose** — one command spins up PostgreSQL + Redis.
- **Extensive docs** — fundamentals, request flows, diagrams, a run guide, and a study order.

---

## 🧰 Tech Stack

**One-line summary:** a **Java 21 + Spring Boot 3** REST API using **Spring Data JPA/Hibernate** over
**PostgreSQL**, with **Redis** caching, built with **Maven** and run on **Docker**.

### Core application stack
| Tech | Version | Role |
|------|---------|------|
| **Java** | 21 (LTS) | Language |
| **Spring Boot** | 3.4.5 | Application framework (auto-config, IoC container, embedded server) |
| **Spring Web (Spring MVC)** | via starter | REST controllers, routing, `DispatcherServlet` |
| **Spring Data JPA** | via starter | Repository abstraction (auto-generated CRUD) |
| **JPA (Jakarta Persistence API)** | 3.x | The persistence *specification* (`@Entity`, `@Id`, …) |
| **Hibernate** | bundled by JPA starter | JPA *implementation* — object ↔ SQL (ORM) |
| **Spring Data Redis** | via starter | `RedisTemplate` + cache integration |

### Data stores
| Tech | Version | Role |
|------|---------|------|
| **PostgreSQL** | 16 | Relational database — source of truth (durable) |
| **Redis** | 7 | In-memory cache for `GET /books/{id}` |

### Supporting libraries (pulled in by the starters)
- **Apache Tomcat** — embedded web server (port 8080)
- **HikariCP** — JDBC connection pool
- **PostgreSQL JDBC Driver** — talks to Postgres over the wire
- **Lettuce** — the Redis client under `RedisTemplate`
- **Jackson** — JSON serialization (HTTP bodies *and* the Redis cache values)
- **SLF4J + Logback** — logging (the `CACHE HIT/MISS` lines)

### Build, tooling & infrastructure
- **Maven** — build tool + dependency management
- **Maven Wrapper (`./mvnw`)** — runs the pinned Maven version, no global install
- **Docker + Docker Compose** — run PostgreSQL + Redis as containers
- **Git / GitHub** — version control + hosting

### Present but not yet used
- **JUnit 5, Spring Boot Test, Mockito** — bundled via `spring-boot-starter-test`; no tests written yet

---

## 🏗️ Architecture

```
                 HTTP (JSON) on :8080
                        │
   ┌────────────────────┼─────────────────────────────────┐
   │  Spring Boot app (embedded Tomcat)                     │
   │            BookController   (HTTP layer)               │
   │                  │                                     │
   │            BookService      (business logic + cache)   │
   │             ├──────────────┐                           │
   │       BookRepository   RedisTemplate                   │
   │       (Hibernate/JDBC)  (Lettuce)                      │
   └───────────┬──────────────────┬────────────────────────┘
               │ SQL :5432         │ get/set :6379
               ▼                   ▼
        ┌──────────────┐    ┌──────────────┐
        │  PostgreSQL   │    │    Redis      │   (both via Docker)
        │ source of     │    │   cache       │
        │ truth         │    │ (disposable)  │
        └──────────────┘    └──────────────┘
```

The **service layer** owns the caching decision (cache-aside): check Redis first, fall back to
PostgreSQL on a miss and populate the cache, and evict on delete. See [`docs/FLOWS.md`](docs/FLOWS.md)
for friendly diagrams and [`docs/REQUEST_FLOW.md`](docs/REQUEST_FLOW.md) for the detailed traces.

---

## 🔌 API Reference

Base URL: `http://localhost:8080`

| Method | Endpoint | Description | Cached? | Success |
|--------|----------|-------------|---------|---------|
| `POST` | `/books` | Create a book | no | `201 Created` |
| `GET` | `/books` | List all books | no | `200 OK` |
| `GET` | `/books/{id}` | Get one book by id | **yes (Redis)** | `200 OK` / `404` |
| `DELETE` | `/books/{id}` | Delete a book (and evict its cache) | evicts | `204 No Content` |

**Book** shape:
```json
{ "id": 1, "title": "Clean Code", "author": "Robert Martin" }
```

---

## 🚀 Getting Started

### Prerequisites
- **JDK 21**
- **Docker** runtime (Docker Desktop, OrbStack, or Colima)

### Run it

```bash
# 1. Start PostgreSQL + Redis
docker compose up -d

# 2. Start the app (compiles and runs from source)
./mvnw spring-boot:run
```

The app is ready when the log shows `Tomcat started on port 8080` and
`Started LibraryManagementApplication`.

> Full step-by-step instructions — including the **reason for each step** and troubleshooting — are in
> **[`docs/HOW_TO_RUN.md`](docs/HOW_TO_RUN.md)**.

### Stop it
```bash
docker compose down    # stop DB + cache (add -v to also wipe data)
```

---

## 🧪 Try it (and watch the cache work)

With the app running, in another terminal:

```bash
# Create a couple of books
curl -s -X POST localhost:8080/books -H 'Content-Type: application/json' \
     -d '{"title":"Clean Code","author":"Robert Martin"}'
curl -s -X POST localhost:8080/books -H 'Content-Type: application/json' \
     -d '{"title":"Effective Java","author":"Joshua Bloch"}'

curl -s localhost:8080/books        # list all

curl -s localhost:8080/books/1      # 1st call -> CACHE MISS (+ a SQL SELECT in the logs)
curl -s localhost:8080/books/1      # 2nd call -> CACHE HIT  (no SQL — served from Redis)

curl -s -X DELETE localhost:8080/books/1   # 204, also evicts the cache
```

**The payoff** — watch the application logs during the two `GET /books/1` calls:

```
CACHE MISS -> book id=1 not in Redis; querying POSTGRES
Hibernate:  select b1_0.id, b1_0.author, b1_0.title from books b1_0 where b1_0.id=?
CACHE FILL -> book id=1 loaded from Postgres and written to Redis (TTL 10 min)
CACHE HIT  -> book id=1 served from REDIS (no DB query)        <-- second call, NO SQL
```

The first request hits PostgreSQL and fills Redis; the second is served entirely from cache. That
contrast is the core idea this project demonstrates.

You can also inspect the cache directly:
```bash
docker exec library-redis redis-cli KEYS '*'      # -> "book:1"
docker exec library-redis redis-cli GET book:1     # -> the cached Book as JSON
```

---

## 📂 Project Structure

```
Library-Management/
├── docker-compose.yml         # PostgreSQL + Redis containers
├── pom.xml                    # Maven build (Java 21, Spring Boot 3.4.5)
├── src/main/
│   ├── java/com/meesho/library/
│   │   ├── LibraryManagementApplication.java   # entry point (@SpringBootApplication)
│   │   ├── controller/BookController.java        # HTTP layer (REST endpoints)
│   │   ├── service/BookService.java              # business logic + cache-aside
│   │   ├── repository/BookRepository.java        # Spring Data JPA interface
│   │   ├── entity/Book.java                       # JPA entity -> "books" table
│   │   └── config/RedisConfig.java               # RedisTemplate bean
│   └── resources/application.properties           # datasource, JPA, Redis, logging
└── docs/                       # in-depth learning documentation
```

---

## 📖 Documentation

This project is documented as a learning resource. Start with the study order:

| Doc | What it covers |
|-----|----------------|
| [`docs/STUDY_ORDER.md`](docs/STUDY_ORDER.md) | The exact order to read the project to learn it |
| [`docs/FLOWS.md`](docs/FLOWS.md) | Big-picture diagrams: startup, who-calls-which, API flows |
| [`docs/HOW_TO_RUN.md`](docs/HOW_TO_RUN.md) | Step-by-step run/test/stop, with the reason for each step |
| [`docs/FUNDAMENTALS.md`](docs/FUNDAMENTALS.md) | Every concept explained (Spring, IoC/DI, JPA, Hibernate, Redis, caching…) |
| [`docs/REQUEST_FLOW.md`](docs/REQUEST_FLOW.md) | Detailed, numbered request lifecycle per endpoint |
| [`docs/PROJECT_STRUCTURE.md`](docs/PROJECT_STRUCTURE.md) | Why each file and package exists |
| [`docs/LEARNING_NOTES.md`](docs/LEARNING_NOTES.md) | Per-annotation/dependency notes (what / why / internals / interview) |

---

## 🎯 Scope

Intentionally **kept minimal** to focus on the core stack. The following are **deliberately excluded**:
authentication/security, DTOs, input validation frameworks, pagination, and any user/borrowing modules.

---

## 📝 License

This is a personal learning project, provided as-is for educational purposes.
