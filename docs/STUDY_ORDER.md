# STUDY_ORDER

> The exact order to read this project to learn Spring Boot from fundamentals to the Redis cache flow.
> Each step says **what to read**, **why now**, and **the one question you should be able to answer**
> before moving on. Read the linked `docs/` section first, then open the code file and read its comments.

The golden rule: **follow the data, not the folder list.** A request enters at the controller and flows
*down*; but to *understand* it, read *up* from the data (entity) to the edge (controller), because each
layer only makes sense once you know the layer beneath it.

```
  STUDY DIRECTION (bottom-up, to learn)         REQUEST DIRECTION (top-down, at runtime)
        entity                                        controller
          ▲                                               │
        repository                                      service
          ▲                                               │
        config (Redis)                                  repository / RedisTemplate
          ▲                                               │
        service                                         Postgres / Redis
          ▲
        controller
```

---

## Stage 0 — Orientation (concepts before code)

1. **`docs/FUNDAMENTALS.md` §1–§7** — the Phase 1 foundation: how Java runs, Maven, IoC/DI, Spring Boot,
   auto-configuration, the boot sequence. *You can't read the code without these.*
   - ✅ *Q: Why is a Spring Boot "hello world" only a few files? What does `@SpringBootApplication` turn on?*

2. **`docs/PROJECT_STRUCTURE.md`** — the file/package map and why layering exists.
   - ✅ *Q: What is each of controller/service/repository/entity/config responsible for, and why must they all live under `com.meesho.library`?*

---

## Stage 1 — Boot & infrastructure (how the app comes alive)

3. **`docker-compose.yml`** — the Postgres + Redis servers the app talks to.
   - ✅ *Q: What ports/credentials do the two servers use, and which is durable vs. disposable?*

4. **`pom.xml`** — the three new dependencies and what each unlocks (read `LEARNING_NOTES.md` →
   "Dependencies added" alongside it).
   - ✅ *Q: Which dependency brings Hibernate? Which brings the Postgres driver? Why is the driver `runtime` scope?*

5. **`src/main/resources/application.properties`** — the datasource, JPA, and Redis settings
   (`docs/FUNDAMENTALS.md` §6).
   - ✅ *Q: What does `ddl-auto=update` do at startup? What does `show-sql=true` let you observe?*

6. **`LibraryManagementApplication.java`** — unchanged entry point; re-read knowing it triggers the
   component scan that wires everything below.
   - ✅ *Q: How do the classes in the next stages get discovered and instantiated?*

---

## Stage 2 — The data and how it's stored (the "down" layers first)

7. **`entity/Book.java`** + **`docs/FUNDAMENTALS.md` §9 (JPA) & §10 (Hibernate)** — the entity and
   object↔table mapping.
   - ✅ *Q: Why does Book need a no-arg constructor? What does `@GeneratedValue(IDENTITY)` mean? What is the difference between JPA (spec) and Hibernate (impl)?*

8. **`repository/BookRepository.java`** + `LEARNING_NOTES.md` → `JpaRepository` — the empty interface
   that becomes a full data layer.
   - ✅ *Q: There's no code here — so where does `findById` come from, and how is it implemented at runtime?*

---

## Stage 3 — The cache wiring

9. **`docs/FUNDAMENTALS.md` §11 (PostgreSQL) & §12 (Redis)** — the two stores compared.
   - ✅ *Q: Why use an in-memory key-value store in front of a relational DB at all?*

10. **`config/RedisConfig.java`** — the customized `RedisTemplate` bean.
    - ✅ *Q: Why configure serializers by hand instead of using the default RedisTemplate? What will a cached value look like in `redis-cli`?*

---

## Stage 4 — The brain (business logic + the cache decision)

11. **`docs/FUNDAMENTALS.md` §13 (Cache & cache-aside)** — read this *before* the service so the code
    confirms a pattern you already understand.
    - ✅ *Q: Trace cache-aside in words: what happens on a miss vs. a hit vs. a delete?*

12. **`service/BookService.java`** — the heart. Read `getById` line by line, then `delete`.
    - ✅ *Q: Exactly which lines produce a CACHE MISS log vs. a CACHE HIT log? Why must `delete` also evict the Redis key?*

13. **`docs/FUNDAMENTALS.md` §8 (DI, concrete)** — revisit DI now that you've seen the constructor that
    receives the repository and RedisTemplate.
    - ✅ *Q: Who calls `new BookService(...)`, and where do its two arguments come from?*

---

## Stage 5 — The edge (HTTP) and the end-to-end flow

14. **`controller/BookController.java`** — the thin HTTP layer.
    - ✅ *Q: How does a JSON body become a `Book`? How does `{id}` become a method argument? Why 201/204?*

15. **`docs/REQUEST_FLOW.md` §1–§5** — the full step-by-step traces, especially **§3 (cache MISS)** vs.
    **§4 (cache HIT)** side by side.
    - ✅ *Q: For two identical `GET /books/1` calls, what differs between the first and second in the logs?*

---

## Stage 6 — Prove it to yourself (run + observe)

16. Start infra and the app, then run the `curl` + `redis-cli` sequence in **`docs/REQUEST_FLOW.md` →
    "How to watch it yourself."** Watch the application console.
    - ✅ *Q: Did the second GET skip the SQL `select`? Did `DELETE` make `GET book:1` return `(nil)` in Redis?*

When you can answer Stage 6's question from the **logs you actually saw**, you understand the whole
project end to end.

---

## After this project (where to go deeper)

- **`@Cacheable` / `@CacheEvict`** — the declarative caching we deliberately skipped; compare it to our
  manual cache-aside.
- **DTOs + Bean Validation (`@Valid`)** — separating the API shape from the entity; validating input.
- **Exception handling** — `@ControllerAdvice` / `@ExceptionHandler` for clean error responses.
- **Transactions** — `@Transactional` and what the persistence context guarantees.
- **Migrations** — Flyway/Liquibase instead of `ddl-auto=update` for real production schema control.
- **Testing** — `@SpringBootTest`, `@DataJpaTest`, Testcontainers for Postgres/Redis in tests.
