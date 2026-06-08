# FUNDAMENTALS

> Every Spring Boot / Java-ecosystem concept introduced in the project, explained from
> first principles for someone coming from MERN / C++ / DSA but new to Java.
>
> This file grows as the project grows. **Phase 1** covers: the Java execution model,
> build tools (Maven), the Spring Framework's core idea, Spring Boot, and how the app boots.
> **Phase 2** (§8 onward) covers the layered architecture (controller/service/repository),
> JPA & Hibernate, PostgreSQL, and Redis caching.

---

## 1. How Java code runs (vs. C++ and Node)

You already know two execution models:

- **C++**: `.cpp` → compiler → **native machine code** (a binary for your exact CPU/OS) → runs on hardware.
- **Node/JS**: `.js` → handed to V8 → interpreted / JIT-compiled **at runtime**. No separate compile step you run.

Java sits **in between**:

```
   Foo.java   ──(javac, compiler)──►   Foo.class   ──(JVM)──►   runs
   (source)                            (bytecode)            (machine code, at runtime)
```

1. You write `.java` **source**.
2. The compiler **`javac`** turns it into **bytecode** (`.class` files) — an instruction set for an
   imaginary "virtual" CPU, *not* your real CPU.
3. The **JVM (Java Virtual Machine)** — a real OS process — loads the bytecode and executes it,
   translating it to actual machine code on the fly via the **JIT (Just-In-Time) compiler**.

**Why it exists / problem solved:** *"Write once, run anywhere."* The same `.class` bytecode runs on
Mac, Windows, Linux, ARM, x86 — each platform ships its own JVM. The price: a runtime translation
cost, in exchange for portability + a managed runtime (automatic memory management / garbage collection).

### JDK vs JRE vs JVM

| Term | What it is | Analogy |
|------|------------|---------|
| **JVM** | The virtual machine that *executes* bytecode | The "engine" |
| **JRE** | JVM + standard libraries needed to **run** programs | Engine + fuel system (run only) |
| **JDK** | JRE + developer tools (`javac`, `jar`, debugger) — enough to **build** | The full workshop |

This project uses **JDK 21** (a Long-Term-Support release). Modern Spring Boot 3.x requires Java 17+.

### `JAVA_HOME`

An environment variable pointing at the JDK install directory. Build tools and IDEs read it to pick
*which* JDK to use when several are installed. In this project it is pinned to JDK 21 in `~/.zshrc`,
because installing Maven via Homebrew also pulled in JDK 26 — without pinning, Maven would silently
build with 26 (too new for parts of the Spring ecosystem). Pin the version you intend to build with.

---

## 2. Build tools & Maven (Java's `npm` + `make`)

In Node you have `package.json` + `npm`; in C++ you have `make`/CMake. Java's equivalent is a **build
tool** — here, **Maven**. It solves three problems:

1. **Dependency management.** Declares the third-party libraries (JAR files) your app needs and
   downloads them — *plus everything they transitively depend on* — from **Maven Central**
   (a public registry, like `npmjs.com`). Downloaded JARs are cached once in `~/.m2/repository`
   and shared across all your projects (unlike `node_modules`, which is copied per project).
2. **Build lifecycle.** Standard phases run in order: `validate → compile → test → package →
   verify → install → deploy`. `mvn package` runs everything up to and including `package`.
3. **Convention over configuration.** A mandated standard folder layout (see `PROJECT_STRUCTURE.md`),
   so every Maven project looks the same.

### `pom.xml` — Maven's `package.json`

The **P**roject **O**bject **M**odel. XML that declares dependencies, the Java version, and build
config. Verbose, but everything is explicit (good for learning).

### Maven "coordinates"

Every artifact is uniquely identified by three parts — like a fully-qualified package name + version:

```
groupId : artifactId : version
  "who"     "what"     "which"
  org.postgresql : postgresql : 42.7.3
```

### Parent POM & the BOM (why our dependencies have no version)

Our `pom.xml` inherits from `spring-boot-starter-parent`. Maven supports **inheritance**: the parent
supplies a curated **BOM** (*Bill of Materials*) — a big list of mutually-compatible dependency
versions. Because the parent already pins versions, our own `<dependency>` entries omit `<version>`,
and we avoid "dependency hell" (incompatible library versions clashing). The Spring Boot version is
declared in exactly one place: the `<parent>` block.

---

## 3. The Spring Framework & its one big idea: IoC / DI

**Spring** is a framework that provides application "plumbing" so you write business logic instead of
infrastructure. Its foundational idea is **Inversion of Control (IoC)** via **Dependency Injection (DI)**.

**Problem it solves:** in a large app, objects depend on other objects (a controller needs a service,
which needs a repository, which needs a DB connection). Wiring all of that by hand (`new`-ing every
dependency) is repetitive, tightly coupled, and hard to test.

**Spring's answer:** a **container** (the `ApplicationContext`) that at startup:
- **creates** the objects your app needs (Spring calls these managed objects **beans**),
- works out which beans depend on which,
- and **injects** the dependencies automatically.

You give up control over object creation to the framework — *that inversion is the "IoC."* You just
declare "I need a `BookService`" and Spring hands you one, already wired.

> In Phase 1 the app has **zero** application beans of our own — but the same container is what boots
> and configures the embedded web server. We'll create real beans (`@RestController`, `@Service`,
> `@Repository`) in later phases, and DI will become concrete then.

**Key terms:**
- **Bean** — an object created and managed by the Spring container.
- **ApplicationContext** — the container itself; the registry of all beans + their wiring.

---

## 4. Spring Boot (why "hello world" is ~3 files)

Plain Spring is powerful but historically needed mountains of manual configuration. **Spring Boot** is
an opinionated layer on top of Spring with three turnkey features:

1. **Starters** — curated dependency *bundles*. One dependency (`spring-boot-starter-web`) transitively
   pulls in Spring MVC + an embedded Tomcat server + Jackson (JSON), all version-matched. Like a
   meta-package that installs a whole stack.
2. **Auto-configuration** — at startup, Spring Boot inspects the **classpath** and configures sensible
   defaults. It sees web libraries → it creates and starts a web server. It sees a DB driver +
   connection settings → it configures a connection pool. This is the "magic": just conditional
   configuration reacting to what's present. You override defaults only when needed.
3. **Embedded server** — the app *contains its own web server* (Tomcat) inside the final jar. You run
   `java -jar app.jar` and it starts listening on a port. No installing/deploying to an external server.

That's why a complete Spring Boot web app can be tiny: one annotated class with a `main()` method.

---

## 5. Anatomy of `@SpringBootApplication`

The single annotation on `LibraryManagementApplication` is the master switch. It bundles three:

| Combined annotation | What it does |
|---------------------|--------------|
| `@SpringBootConfiguration` | Marks this class as a source of bean definitions. |
| `@EnableAutoConfiguration` | Turns on auto-configuration (sees Tomcat on classpath → starts a web server). |
| `@ComponentScan` | Scans **this package** (`com.meesho.library`) and **all sub-packages** for Spring components (`@RestController`, `@Service`, `@Repository`, …) and registers them as beans. |

**Consequence for later phases:** any class we annotate as a Spring component will be auto-discovered
*as long as it lives under `com.meesho.library`*. That's why package placement matters.

`SpringApplication.run(LibraryManagementApplication.class, args)` then:
1. Creates the `ApplicationContext`.
2. Runs auto-configuration.
3. Performs component scanning and wires beans (DI).
4. Starts the embedded Tomcat server.

---

## 6. External configuration: `application.properties`

Spring Boot automatically reads `src/main/resources/application.properties` at startup. It holds
`key=value` settings that override Spring Boot defaults, keeping configuration **out of code**
(12-factor style). Phase 1 sets:

```properties
spring.application.name=library-management   # name shown in logs/tooling
server.port=8080                              # port the embedded Tomcat listens on
```

There are hundreds of recognized keys (DB URL, connection-pool size, logging levels, …); we add them
as features arrive.

---

## 7. The observed startup flow (Phase 1)

Running `java -jar target/library-management-0.0.1-SNAPSHOT.jar` produced:

```
Starting LibraryManagementApplication v0.0.1-SNAPSHOT using Java 21.0.11 ...   # main() → run() began
Tomcat initialized with port 8080 (http)                                       # auto-config created Tomcat
Tomcat started on port 8080 (http) with context path '/'                       # server listening
Started LibraryManagementApplication in 0.618 seconds ...                      # context ready
```

A `GET http://localhost:8080/` returned **HTTP 404** with a JSON error body. That 404 is the expected,
*correct* result for Phase 1: the request reached the server and Spring's request dispatcher, which
found no handler for `/` (we have no endpoints yet) and returned a clean 404. The infrastructure is
proven; it's simply empty. See `REQUEST_FLOW.md` for the step-by-step path.

---

## 8. The layered architecture (Controller → Service → Repository)

Phase 2 introduces three classes that form a **layered architecture**. Each layer has ONE job and only talks to the layer directly below it:

```
   HTTP request
        │
        ▼
 ┌──────────────────┐   CONTROLLER  (@RestController)
 │  BookController   │   - speaks HTTP: routing, JSON in/out, status codes, path vars
 │                   │   - NO business logic. Translates HTTP <-> method calls.
 └────────┬─────────┘
          ▼
 ┌──────────────────┐   SERVICE  (@Service)
 │   BookService     │   - business logic: the cache-aside rules, what-happens-when
 │                   │   - knows nothing about HTTP; knows nothing about SQL details
 └───┬──────────┬───┘
     ▼          ▼
 ┌─────────┐ ┌──────────────┐  REPOSITORY (Spring Data) + the cache client
 │BookRepo │ │ RedisTemplate │  - data access only: CRUD on Postgres / GET-SET on Redis
 └────┬────┘ └──────┬───────┘
      ▼             ▼
  PostgreSQL      Redis
```

**Why split it like this?** Separation of concerns — the same reason you split routes / controllers / models in Express:

- **Testable**: you can test `BookService`'s caching logic without starting a web server.
- **Swappable**: change the HTTP framework and only the controller changes; change the DB and only the repository changes.
- **Readable**: each file answers one question — "how is it exposed?" (controller), "what should happen?" (service), "how is it stored?" (repository).

> **Mapping to MERN:** Controller ≈ Express route handler; Service ≈ your business-logic module; Repository ≈ the Mongoose model / data layer. The big difference is you don't `new` or `require` these — the IoC container (§3) creates one of each and injects them.

### Dependency Injection, now concrete

In Phase 1, DI was abstract (we had no beans of our own). Now it is real. `BookService` declares what it needs in its **constructor**:

```java
public BookService(BookRepository bookRepository, RedisTemplate<String, Book> redisTemplate) { ... }
```

At startup the container sees this constructor, finds the matching beans it already created (the repository proxy, the RedisTemplate), and passes them in. You never call `new BookService(...)`. This is **constructor injection** — preferred because:
- the object is fully built and valid the instant it exists (no half-initialized state),
- the fields can be `final` (immutable),
- in a unit test you can just call `new BookService(mockRepo, mockRedis)` with fakes.

With a single constructor, `@Autowired` is not even needed — Spring infers it.

---

## 9. JPA — the *specification* (the "what")

**JPA = Jakarta Persistence API.** It is a **specification**: a set of interfaces and annotations (`@Entity`, `@Id`, `EntityManager`, ...) that defines *how Java objects map to relational tables* — but contains **no working code**. Think of it as an interface/contract, like the C++ STL's iterator concept or a TypeScript `interface`: it describes the shape, not the implementation.

**Problem it solves:** without an ORM you'd write SQL strings everywhere and manually copy each column into object fields (`rs.getString("title")`). That's the JDBC world — verbose and error-prone. JPA lets you say "`Book` is an entity; `id` is its key" once, declaratively, and work with objects.

Key JPA annotations we use:

| Annotation | Meaning |
|------------|---------|
| `@Entity` | "instances of this class are persistent — map them to table rows" |
| `@Table(name="books")` | which table to map to (default = class name) |
| `@Id` | this field is the primary key |
| `@GeneratedValue(strategy=IDENTITY)` | the database generates the key (Postgres identity/auto-increment) |

---

## 10. Hibernate — the *implementation* (the "how")

**Hibernate** is the most popular **implementation** of the JPA specification — the actual engine that does the work. JPA is the interface; Hibernate is the concrete class behind it. (Spring Boot's JPA starter ships Hibernate as the default.)

```
   Your code (JPA API)            Hibernate (implementation)        JDBC + driver
 ┌──────────────────────┐   ┌──────────────────────────────┐   ┌────────────────┐
 │ bookRepository        │──►│ build SQL, manage the         │──►│ PostgreSQL JDBC │──► Postgres
 │   .findById(1L)       │   │ "persistence context", map    │   │ driver (socket) │
 │ (you think in objects)│   │ rows <-> Book objects         │   │ (speaks wire    │
 └──────────────────────┘   └──────────────────────────────┘   │  protocol)      │
                                                                 └────────────────┘
```

**What Hibernate does for `findById(1L)`:**
1. Generates SQL: `select b.id, b.title, b.author from books b where b.id = ?`.
2. Hands it (with the parameter `1`) to the JDBC driver, which runs it on Postgres.
3. Takes the returned row and **hydrates** a `Book` object — `new Book()` + set each field from each column.
4. Returns the `Book`.

**ORM = Object-Relational Mapping**: the whole job of bridging the "object world" (Java classes, references) and the "relational world" (tables, rows, foreign keys). With `spring.jpa.show-sql=true` you will literally watch Hibernate's generated SQL in the console — the best way to *see* the ORM working.

> **`ddl-auto=update`**: on startup Hibernate compares your `@Entity` classes to the actual tables and issues `CREATE TABLE` / `ALTER TABLE` to make the DB match. Convenient for learning; real production uses explicit migration tools (Flyway/Liquibase) because auto-DDL can be surprising and is hard to review.

---

## 11. PostgreSQL — the database (source of truth)

**PostgreSQL** is a mature, open-source **relational database (RDBMS)**: data lives in **tables** of typed **columns**, rows are uniquely identified by a **primary key**, and you query with **SQL**. It is **durable** — data survives restarts (written to disk) — which is why it is our **source of truth**.

Coming from MongoDB (MERN): Mongo is a *document* store (flexible JSON-like documents, no fixed schema); Postgres is *relational* (fixed schema, strong typing, transactions/ACID guarantees). For a Library system with well-defined records and relationships, relational is the natural fit.

- We talk to it over **JDBC** (Java Database Connectivity) — Java's standard DB API — using the **PostgreSQL JDBC driver** (the `postgresql` dependency). The driver is the only thing that actually speaks Postgres's network protocol.
- Connections are expensive to open, so a **connection pool** (HikariCP, auto-configured by the JPA starter) keeps a set of open connections ready and hands them out.
- In this project Postgres runs as a Docker container on `localhost:5432`, database `library`.

---

## 12. Redis — the cache (fast, in-memory)

**Redis = REmote DIctionary Server.** An in-memory **key-value store**: at its core a giant hash map (`key -> value`) that lives in **RAM**, reachable over the network. Because it skips disk and complex query planning, reads/writes are sub-millisecond.

| | PostgreSQL | Redis |
|---|---|---|
| Stores | tables/rows on **disk** | key→value in **RAM** |
| Speed | fast | *very* fast (µs–ms) |
| Role here | **source of truth** (durable) | **cache** (disposable copy) |
| Query | rich SQL | get/set by key |

We use Redis as a **cache** in front of Postgres for `GET /books/{id}`. Keys look like `book:1`; values are the `Book` serialized to JSON. We also give each entry a **TTL** (time-to-live, 10 min) so stale data eventually self-cleans. Redis runs as a Docker container on `localhost:6379`.

> **`RedisTemplate`** is the Spring object we use to talk to Redis: `redisTemplate.opsForValue().get(key)` / `.set(key, value, ttl)`. We configured it (in `RedisConfig`) to store keys as strings and values as JSON so they're human-readable in `redis-cli`.

---

## 13. Cache & the cache-aside pattern (the core of Phase 2)

A **cache** is a small, fast copy of data you keep close so you don't repeatedly pay for the slow source. The cost: the copy can go **stale**, so you need a strategy to keep it correct.

We use **cache-aside** (a.k.a. lazy loading) — the most common app-managed pattern. The *application* (our `BookService`) owns the logic:

**Read (`GET /books/{id}`):**
```
        ┌─────────────────── GET /books/1 ───────────────────┐
        ▼                                                     │
   look in Redis (key "book:1")                               │
        │                                                     │
   ┌────┴─────┐                                               │
   │ found?   │                                               │
   └────┬─────┘                                               │
    yes │ no                                                  │
        │  └──► CACHE MISS: query Postgres ─► got Book ─► SET "book:1"=Book in Redis (TTL) ─┐
        │                                                                                    │
        └──► CACHE HIT: return Book straight from Redis (NO Postgres query) ◄────────────────┘
                                          │
                                          ▼
                                   return Book to client
```

- **Cache MISS** = the key was not in Redis. Cost: one Redis lookup (miss) + one Postgres query + one Redis write. The *first* request for any id is always a miss.
- **Cache HIT** = the key was in Redis. Cost: one Redis lookup, and **the database is never touched**. Every repeat request (within the TTL) is a hit.

**Write/delete consistency:** when we `DELETE /books/{id}`, we remove the row from Postgres **and evict** `book:{id}` from Redis. If we forgot to evict, a later GET would hit the stale cache and return a "ghost" book that no longer exists. **Keeping the cache consistent with the source of truth is the hard part of caching** — here we handle it by evicting on delete and relying on the TTL as a safety net.

> **Why not cache `GET /books` (the list) too?** It would need invalidating on *every* create/delete, which is more bookkeeping. We keep the demo focused: only `GET /books/{id}` is cached. This is a deliberate simplification, not an oversight.

**Three eviction triggers in this project:**
1. **Explicit** — we `DELETE` the key when the book is deleted.
2. **TTL** — Redis auto-expires the key after 10 minutes.
3. **Flush** — if Redis restarts (no volume in our compose file) it's empty; the app just rebuilds it from Postgres on the next miss.

---

## Glossary (quick reference)

- **Bytecode** — platform-independent instructions in `.class` files, executed by the JVM.
- **JVM / JRE / JDK** — runtime engine / runtime+libs / full dev kit (see §1).
- **Maven Central** — public registry of Java libraries (like npmjs.com).
- **`~/.m2/repository`** — local cache of downloaded dependencies.
- **Coordinates** — `groupId:artifactId:version`, a unique artifact identifier.
- **BOM** — Bill of Materials; a curated set of compatible dependency versions.
- **Starter** — a Spring Boot dependency bundle (e.g. `spring-boot-starter-web`).
- **Auto-configuration** — Spring Boot configuring defaults based on the classpath.
- **Bean** — an object created/managed by the Spring container.
- **ApplicationContext** — the Spring container holding all beans.
- **IoC / DI** — Inversion of Control / Dependency Injection (framework creates & wires objects).
- **Embedded server** — a web server (Tomcat) bundled inside the app jar.
- **Fat jar / executable jar** — a single jar containing your code + all deps + the server.
- **Controller / Service / Repository** — the three layers: HTTP / business-logic / data-access.
- **JPA** — Jakarta Persistence API; the *specification* (annotations + interfaces) for object↔table mapping.
- **Hibernate** — the most common *implementation* of JPA; generates SQL and maps rows ↔ objects.
- **ORM** — Object-Relational Mapping; bridging the object world and the relational (table) world.
- **Entity** — a class (`@Entity`) whose instances map to rows of a table.
- **Spring Data JPA** — generates repository implementations from interfaces at runtime.
- **JDBC** — Java's standard low-level database API; Hibernate uses it under the hood.
- **Connection pool (HikariCP)** — a reusable set of open DB connections, handed out on demand.
- **DDL-auto** — Hibernate auto-creating/altering tables from your entities on startup.
- **PostgreSQL** — the relational database; durable source of truth (disk, SQL, ACID).
- **Redis** — in-memory key→value store; used here as a cache (fast, disposable).
- **RedisTemplate** — the Spring client object for reading/writing Redis.
- **Cache** — a fast, nearby copy of data to avoid repeatedly hitting the slow source.
- **Cache-aside** — pattern where the app checks the cache, falls back to the DB on a miss, and populates the cache.
- **Cache hit / miss** — value found in the cache (no DB call) / not found (DB call + cache fill).
- **TTL** — time-to-live; how long a cache entry lives before auto-expiring.
- **Eviction** — removing an entry from the cache (explicitly, or via TTL/flush).
