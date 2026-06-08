# FLOWS

> The **big-picture map** of the app, in pictures and plain language: how it starts, **which part calls
> which**, and how each API request flows through. This is the friendly overview — for the detailed,
> numbered, SQL-level trace of each endpoint see [`REQUEST_FLOW.md`](REQUEST_FLOW.md).

There are only **two kinds of flow** in any Spring Boot app:

1. **Startup flow** — happens **once**, when you launch the app. Builds and wires everything.
2. **Request flow** — happens **per HTTP call**, reusing the things startup built.

---

## 1. The cast (who's who)

```
  BookController   the front door   — receives HTTP, speaks JSON
  BookService      the brain        — decides "cache or database?" (business logic)
  BookRepository   the librarian    — fetches/stores rows (talks to the database)
  RedisTemplate    the notebook     — quick scratch copy of recent answers (the cache)
  Book             the data         — one book = one row = one cached value
  PostgreSQL       the archive       — permanent, on disk, source of truth
  Redis            the sticky note   — fast, in memory, a throwaway copy
```

Plain-language version: **the front door hands the request to the brain. The brain checks its sticky
note first; if the answer isn't there, it asks the librarian to read the archive, then writes the answer
on a sticky note so next time is instant.**

---

## 2. Startup flow (runs once, when you `./mvnw spring-boot:run`)

This is the "assemble the machine" phase. Nothing handles requests yet — Spring is *building and
connecting* all the parts.

```
   main()
     │  calls
     ▼
   SpringApplication.run(LibraryManagementApplication.class)
     │
     ▼
   ┌──────────────────────────────────────────────────────────────┐
   │  1. Create the IoC container (ApplicationContext)              │
   │     = the "box" that will hold and connect all our objects     │
   └───────────────────────────────┬──────────────────────────────┘
                                    ▼
   ┌──────────────────────────────────────────────────────────────┐
   │  2. AUTO-CONFIGURATION (reacts to what's on the classpath)     │
   │     sees JPA + Postgres driver  ─►  builds DataSource (Hikari)  │
   │                                     + Hibernate                 │
   │     sees Redis libs             ─►  builds RedisConnectionFactory│
   └───────────────────────────────┬──────────────────────────────┘
                                    ▼
   ┌──────────────────────────────────────────────────────────────┐
   │  3. COMPONENT SCAN of com.meesho.library finds our parts:      │
   │     @RestController, @Service, @Configuration, repository iface │
   └───────────────────────────────┬──────────────────────────────┘
                                    ▼
   ┌──────────────────────────────────────────────────────────────┐
   │  4. CREATE + WIRE the beans, bottom-up (dependency order):     │
   │       RedisConfig.bookRedisTemplate()  ─► makes RedisTemplate   │
   │       Spring Data                       ─► makes BookRepository  │
   │                                             (a generated proxy)  │
   │       new BookService(repository, redisTemplate)  ◄─ injected    │
   │       new BookController(service)                 ◄─ injected    │
   └───────────────────────────────┬──────────────────────────────┘
                                    ▼
   ┌──────────────────────────────────────────────────────────────┐
   │  5. Hibernate (ddl-auto=update) ─► CREATE TABLE books if needed │
   │  6. Embedded Tomcat starts ─► now LISTENING on :8080            │
   └──────────────────────────────────────────────────────────────┘
                                    ▼
                        "Started LibraryManagementApplication"
                              (the app is now ready)
```

**The one idea to take away:** you never wrote `new BookController(new BookService(...))`. The container
built each object once and **injected** the dependencies. By the end of startup, the objects are
connected like this 👇 and just sit waiting for requests.

---

## 3. The wiring — *which part calls which* (the call graph)

After startup, the objects hold references to each other in a straight line. Calls only ever go
**downward** (top calls down; nothing calls back up):

```
        ┌────────────────────┐
        │   BookController    │   holds ──► bookService
        └─────────┬──────────┘
                  │ calls bookService.create / getAll / getById / delete
                  ▼
        ┌────────────────────┐
        │    BookService      │   holds ──► bookRepository  AND  ──► redisTemplate
        └───┬────────────┬───┘
            │            │
   calls    │            │  calls redisTemplate.opsForValue().get/set, .delete
   repo.    │            │
   findById │            ▼
   /save/   │      ┌──────────────┐         ┌───────────────┐
   findAll/ │      │ RedisTemplate │ ──────► │     Redis      │  (sticky note)
   deleteById      └──────────────┘         └───────────────┘
            ▼
     ┌────────────────┐    generates SQL via    ┌───────────────┐
     │ BookRepository  │ ─────────────────────► │   Hibernate    │ ─► PostgreSQL (archive)
     │ (Spring Data)   │                         └───────────────┘
     └────────────────┘
```

- **Controller → Service**: one reference, injected at startup.
- **Service → Repository** (for the database) **and Service → RedisTemplate** (for the cache): the
  service is the only layer that talks to *both*, because the **"cache or database?" decision lives in
  the service**.
- **Repository → Hibernate → PostgreSQL**: the repository is just an interface; Hibernate turns its
  method calls into SQL and the Postgres driver runs them.

Why one-directional? It keeps responsibilities clean: change how books are *stored* and only the
repository area changes; change how they're *exposed over HTTP* and only the controller changes.

---

## 4. API flows (per request) — simple version

Every request enters Tomcat → the `DispatcherServlet` (Spring's single "traffic cop") → the matching
controller method. Below, that prefix is assumed; we focus on **who calls whom** after that.

### `POST /books` — "save a new book"
```
You ─JSON─► Controller ─Book─► Service ─save─► Repository ─► Hibernate ─INSERT─► Postgres
   ◄──────────────── JSON {id,...} ◄──────────── saved Book (now has id) ◄───────┘
                         (cache is NOT touched on create)
```
Plain: turn JSON into a Book, store it, hand back the stored Book (now with an id). **201 Created.**

### `GET /books` — "list all books"
```
You ─► Controller ─► Service ─findAll─► Repository ─► Hibernate ─SELECT *─► Postgres
   ◄── JSON array ◄────────────────────────────────────────────────────────┘
                       (the list is NOT cached)
```
Plain: read every row, return them as a JSON array. **200 OK.**

### `GET /books/{id}` — the cached one (TWO possible paths)

**Path A — CACHE MISS (first time for this id):**
```
You ─► Controller ─► Service ──(1) ask Redis──► (empty!)            ✗ not on the sticky note
                        │
                        ├──(2) ask Postgres──► Repository ─► Hibernate ─SELECT─► Postgres ─► found
                        │
                        └──(3) write to Redis (TTL 10m)             ✎ jot it on a sticky note
   ◄── JSON Book ◄──────┘
   log:  CACHE MISS  +  CACHE FILL   (and you SEE a SELECT in the log)
```

**Path B — CACHE HIT (every repeat within 10 min):**
```
You ─► Controller ─► Service ──ask Redis──► found! ─► return        ✓ already on the sticky note
   ◄── JSON Book ◄──┘
   log:  CACHE HIT   (NO SELECT — Postgres is never touched)
```

Plain: check the sticky note first. Miss → read the archive *and* make a sticky note. Hit → answer
straight from the sticky note, skipping the archive entirely. **That MISS-then-HIT difference is the
whole point of using Redis.**

### `DELETE /books/{id}` — "remove a book (and its sticky note)"
```
You ─► Controller ─► Service ─┬─ deleteById ─► Repository ─► Hibernate ─(SELECT then DELETE)─► Postgres
                              │
                              └─ delete the Redis key   ✂ tear up the sticky note
   ◄── 204 No Content ◄───────┘
```
Plain: delete the row **and** evict the cached copy — otherwise a later GET would be a HIT and return a
"ghost" book that no longer exists. (Note: Spring Data's `deleteById` does a `SELECT` to load the row,
then a `DELETE` — you'll see both in the log.) **204 No Content.**

---

## 5. The whole thing in one picture

```
                 ┌──────────────────── your machine ────────────────────┐
                 │                                                       │
   curl / app ──HTTP :8080──►  ┌───────────── JVM (the app) ──────────┐  │
                 │             │  Tomcat → DispatcherServlet           │  │
                 │             │      → BookController  (HTTP)         │  │
                 │             │      → BookService     (logic+cache)  │  │
                 │             │         ├─ BookRepository → Hibernate │  │
                 │             │         └─ RedisTemplate             │  │
                 │             └──────────┬───────────────┬───────────┘  │
                 │                        │ SQL :5432      │ get/set :6379 │
                 │                        ▼                ▼               │
                 │              ┌──────────────┐   ┌──────────────┐       │
                 │   (Docker)   │  PostgreSQL   │   │    Redis      │      │
                 │              │ table "books" │   │ key "book:id" │      │
                 │              │  SOURCE OF    │   │   CACHE       │      │
                 │              │   TRUTH       │   │ (disposable)  │      │
                 │              └──────────────┘   └──────────────┘       │
                 └───────────────────────────────────────────────────────┘
```

**Read it as:** HTTP comes in on 8080 → controller → service → (cache first, database second) → answer
goes back out. The two stores live in Docker; the app talks to them as a client.

---

### Where to go next
- Want the **numbered, SQL-level** version of these flows? → [`REQUEST_FLOW.md`](REQUEST_FLOW.md)
- Want to **run it** and watch these flows in the logs? → [`HOW_TO_RUN.md`](HOW_TO_RUN.md)
- Want the **why** behind each concept (IoC, JPA, cache-aside…)? → [`FUNDAMENTALS.md`](FUNDAMENTALS.md)
