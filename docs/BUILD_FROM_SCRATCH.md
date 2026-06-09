# BUILD_FROM_SCRATCH

> How to build this entire project **yourself, from an empty folder**, in the order a real developer
> actually builds it. Each step has **🎯 what · 🤔 why · 🛠️ how · ✅ verify**. Follow it top to bottom;
> don't skip the "verify" checkpoints — they catch mistakes early, while they're still cheap to fix.

> **Golden rule of building (≠ studying):** build **bottom-up and in slices**. Get a small piece working
> and *tested* before adding the next. You will build the whole CRUD app on PostgreSQL **first**, prove
> it works, and only **then** add Redis caching. Never write all the code and debug at the end.

---

## The build flow at a glance

```
 PHASE 0            PHASE 1           PHASE 2          PHASE 3          PHASE 4 (in slices)        PHASE 5
 Plan &     ──►     Scaffold   ──►    Infra     ──►    Configure  ──►   Code bottom-up      ──►    Run &
 prereqs           (Initializr)      (Docker)         (properties)     + test each slice          test all
                                                                        │
                                          ┌─────────────────────────────┴───────────────────────────┐
                                          │  4a Entity  → run, verify table created                   │
                                          │  4b Repository                                            │
                                          │  4c Service (NO cache yet) ─┐                              │
                                          │  4d Controller             ─┴► TEST full CRUD on Postgres │
                                          │  4e RedisConfig (RedisTemplate bean)                      │
                                          │  4f Add cache-aside to Service ──► TEST cache MISS/HIT    │
                                          └───────────────────────────────────────────────────────────┘
```

Why this shape? Each phase produces something you can **check** before moving on: a booting app, running
containers, a created table, working CRUD, then a working cache. If step N breaks, you *know* it was step
N — not a mystery buried in 8 files.

---

## PHASE 0 — Plan & prerequisites

### Step 0.1 — Write down what you're building
🎯 A one-paragraph spec.
🤔 You can't build what you haven't defined. Pin the scope so you don't wander.
🛠️ Decide:
- **Entity:** `Book { id, title, author }`
- **Endpoints:** `POST /books`, `GET /books`, `GET /books/{id}`, `DELETE /books/{id}`
- **Stack:** Java 21, Spring Boot, PostgreSQL (store), Redis (cache on `GET /books/{id}`)
- **Out of scope (on purpose):** auth, DTOs, validation, pagination, users/borrowing.
✅ You can say in one sentence what the app does.

### Step 0.2 — Install & verify the toolchain
🎯 JDK 21, Docker, and Maven available.
🤔 No toolchain → nothing compiles or runs. Pin **Java 21** so Maven doesn't silently use a different JDK.
🛠️
```bash
java -version          # should report 21
echo $JAVA_HOME        # should point to a JDK 21
docker --version       # any recent Docker runtime (Desktop / OrbStack / Colima)
```
✅ All three print sane versions. (Maven comes via the wrapper in the next phase — no global install needed.)

---

## PHASE 1 — Scaffold the project

### Step 1.1 — Generate the skeleton with Spring Initializr
🎯 A standard Spring Boot project (pom.xml, main class, Maven wrapper, folder layout).
🤔 **Don't hand-write boilerplate.** [start.spring.io](https://start.spring.io) is the conventional
starting point — it produces the exact standard layout and a correct `pom.xml` with version-matched
dependencies. (You *could* hand-write `pom.xml`, but Initializr removes a whole class of mistakes.)
🛠️ On start.spring.io choose:
- **Project:** Maven · **Language:** Java · **Spring Boot:** the latest **3.x** version it offers
- **Group:** `com.meesho` · **Artifact:** `library-management` · **Java:** 21
- **Dependencies:** `Spring Web`, `Spring Data JPA`, `PostgreSQL Driver`, `Spring Data Redis`

Then **Generate** → unzip → open the folder in your IDE.

> 🧠 **About the version:** this repo is pinned to Spring Boot **3.4.5**. Initializr only lists
> *currently-supported* versions, so 3.4.5 may no longer appear — just take the newest **3.x** it offers
> (the code here is standard across Spring Boot 3 and works unchanged). If you want an exact match, pick
> any 3.x, then edit the `<version>` in the generated `pom.xml`'s `<parent>` block to `3.4.5`.
✅ You have `pom.xml`, `src/main/java/com/meesho/library/LibraryManagementApplication.java`, `mvnw`, and
`src/main/resources/application.properties`.

> 🧠 **Why those 4 dependencies map to our needs:** `Web` = REST controllers + embedded Tomcat;
> `Data JPA` = Hibernate + repositories; `PostgreSQL Driver` = talk to Postgres; `Data Redis` = the cache.

### Step 1.2 — Understand what you just got (don't run it yet)
🎯 Know the entry point before adding to it.
🤔 `@SpringBootApplication` on the main class triggers the component scan rooted at `com.meesho.library`.
**Everything you create must live under that package** to be discovered.
🛠️ Read the generated main class.
⚠️ **Do not run the app yet.** You added JPA + the Postgres driver, so Spring Boot will try to configure a
datasource on startup and **fail** (`Failed to configure a DataSource…`) until Postgres exists and is
configured. That's expected — we fix it in Phases 2–3 *before* the first run.
✅ You understand why running now would fail.

---

## PHASE 2 — Stand up the infrastructure (Docker)

### Step 2.1 — Write `docker-compose.yml`
🎯 Declare PostgreSQL + Redis as containers.
🤔 The app connects to these on startup. Defining them as code makes the environment reproducible (no
manual installs). Do this **before** the app runs so the datasource has something to connect to.
🛠️ Create `docker-compose.yml` in the project root with a `postgres:16` service (db/user/pass =
`library`, port `5432`, a named volume for data) and a `redis:7` service (port `6379`, no volume — a
cache is disposable). *(See the finished [`docker-compose.yml`](../docker-compose.yml) for the exact shape.)*
✅ File exists with both services and matching credentials you'll reuse in Phase 3.

### Step 2.2 — Start and verify the containers
🎯 Both servers actually running and accepting connections.
🤔 Postgres takes a few seconds to initialize on first boot; connecting too early fails.
🛠️
```bash
docker compose up -d
docker exec library-postgres pg_isready -U library   # -> accepting connections
docker exec library-redis redis-cli ping             # -> PONG
```
✅ `pg_isready` says accepting connections and Redis says `PONG`.

---

## PHASE 3 — Configure the app

### Step 3.1 — Fill in `application.properties`
🎯 Tell Spring where Postgres and Redis are, and how Hibernate should behave.
🤔 Auto-configuration reads these keys to build the connection pool, Hibernate, and the Redis client.
Without them the JPA starter has no datasource and the app fails to boot.
🛠️ Set:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/library
spring.datasource.username=library
spring.datasource.password=library

spring.jpa.hibernate.ddl-auto=update      # Hibernate creates/updates tables from your entities
spring.jpa.show-sql=true                   # print SQL so you can SEE what Hibernate does
spring.jpa.properties.hibernate.format_sql=true

spring.data.redis.host=localhost
spring.data.redis.port=6379

logging.level.com.meesho.library=INFO      # so your own log lines show up
```
*(Full annotated version: [`application.properties`](../src/main/resources/application.properties).)*

### Step 3.2 — First successful boot (empty app)
🎯 Prove the wiring (app ↔ Postgres ↔ Redis) is correct *before* writing features.
🛠️
```bash
./mvnw spring-boot:run
```
✅ Logs show `HikariPool-1 - Start completed`, `Tomcat started on port 8080`, and
`Started LibraryManagementApplication`. **No table yet** (you have no entity) — that's fine. Stop with
Ctrl+C. **This is your first real checkpoint: the skeleton runs end-to-end.**

> 🧠 If it fails here, it's a connection/config problem (wrong port, Postgres not up, typo in a key) —
> *not* your business code, because you haven't written any yet. That's the value of testing this slice.

---

## PHASE 4 — Write the code, bottom-up, one slice at a time

Build in the order of **dependency**: each piece needs the one before it. Test CRUD on Postgres **before**
touching Redis.

### Step 4a — The Entity (`entity/Book.java`)
🎯 The class Hibernate maps to the `books` table.
🤔 It's the data foundation — repository, service, controller, and the cache all pass `Book` around. Build it first.
🛠️ Create package `entity`, class `Book`:
```java
@Entity
@Table(name = "books")
public class Book {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String author;
    // no-arg constructor (JPA requires it) + getters/setters
}
```
✅ **Verify the table gets created:** run the app, then in another terminal:
```bash
docker exec library-postgres psql -U library -d library -c '\dt'   # should list "books"
```
Stop the app. You just proved the ORM mapping works.

### Step 4b — The Repository (`repository/BookRepository.java`)
🎯 Data-access layer — for free.
🤔 Spring Data generates the implementation; you only declare the interface.
🛠️
```java
public interface BookRepository extends JpaRepository<Book, Long> { }
```
✅ It compiles. (Nothing to test visibly yet — it's consumed by the service next.)

### Step 4c — The Service, *without cache yet* (`service/BookService.java`)
🎯 Business logic that just delegates to the repository — **no Redis at this stage.**
🤔 Get CRUD correct before adding caching complexity. One new concept at a time.
🛠️ Inject the repository via the constructor; implement `create/getAll/getById/delete` calling
`bookRepository.save/findAll/findById/deleteById`. For a missing id, throw
`ResponseStatusException(HttpStatus.NOT_FOUND, ...)`.
```java
@Service
public class BookService {
    private final BookRepository repo;
    public BookService(BookRepository repo) { this.repo = repo; }   // constructor injection (DI)
    // create / getAll / getById / delete — pure repository calls for now
}
```
✅ Compiles. (Tested via the controller in the next step.)

### Step 4d — The Controller (`controller/BookController.java`)
🎯 Expose the 4 endpoints over HTTP.
🤔 Now you can actually call the app and test the whole CRUD slice.
🛠️
```java
@RestController
@RequestMapping("/books")
public class BookController {
    private final BookService service;
    public BookController(BookService service) { this.service = service; }

    @PostMapping  @ResponseStatus(HttpStatus.CREATED)
    public Book create(@RequestBody Book b) { return service.create(b); }

    @GetMapping                public List<Book> all()            { return service.getAll(); }
    @GetMapping("/{id}")       public Book one(@PathVariable Long id){ return service.getById(id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
```
✅ **CHECKPOINT — full CRUD works on Postgres (no cache):** run the app and:
```bash
curl -s -X POST localhost:8080/books -H 'Content-Type: application/json' -d '{"title":"Clean Code","author":"Robert Martin"}'
curl -s localhost:8080/books
curl -s localhost:8080/books/1
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE localhost:8080/books/1
```
Every `GET /books/{id}` shows a Hibernate `SELECT` in the logs (no cache yet — expected). **Stop here and
make sure this works before adding Redis.**

### Step 4e — Redis config (`config/RedisConfig.java`)
🎯 A `RedisTemplate<String, Book>` that stores keys as strings and values as JSON.
🤔 The default template serializes to unreadable binary; you want readable JSON so you can inspect the cache.
🛠️
```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Book> bookRedisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, Book> t = new RedisTemplate<>();
        t.setConnectionFactory(cf);
        t.setKeySerializer(new StringRedisSerializer());
        t.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return t;
    }
}
```
✅ App still boots (the new bean is created at startup).

### Step 4f — Add cache-aside to the service
🎯 Make `getById` check Redis first, fall back to Postgres on a miss (and cache the result); make `delete`
evict the key. Add log lines for HIT/MISS.
🤔 This is the whole point of the project — and you're adding it *last*, on top of code you already know works.
🛠️ Inject `RedisTemplate<String, Book>` into the service too. In `getById`:
```java
Book cached = redis.opsForValue().get("book:" + id);
if (cached != null) { log.info("CACHE HIT ..."); return cached; }     // hit -> no DB
log.info("CACHE MISS ...");
Book book = repo.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, ...));
redis.opsForValue().set("book:" + id, book, Duration.ofMinutes(10));  // fill cache (TTL)
return book;
```
In `delete`: after `repo.deleteById(id)`, call `redis.delete("book:" + id)` (evict, so no stale "ghost").
*(Full version: [`BookService.java`](../src/main/java/com/meesho/library/service/BookService.java).)*
✅ **CHECKPOINT — the cache works:** run the app, then:
```bash
curl -s localhost:8080/books/1     # -> log: CACHE MISS + a SELECT
curl -s localhost:8080/books/1     # -> log: CACHE HIT, NO SELECT
```
That MISS-then-HIT is the finish line.

---

## PHASE 5 — Full run & test

🎯 Exercise everything end-to-end and watch the flows.
🛠️ Follow [`HOW_TO_RUN.md`](HOW_TO_RUN.md) Steps 4–5 (the full curl sequence + `redis-cli` inspection),
watching the app logs for `CACHE HIT/MISS` and Hibernate SQL.
✅ POST/GET/GET-by-id (miss→hit)/DELETE all behave; Redis shows `book:1` with a TTL; delete evicts it.

---

## PHASE 6 — (Optional) Document & push to GitHub

🎯 Make it shareable.
🛠️
- Add a `.gitignore` that excludes `target/` and IDE files (so build output isn't committed).
- Write a `README.md` (see this repo's [`README.md`](../README.md)).
- `git init` → `git add .` → `git commit -m "Library Management System"` → create a GitHub repo → push.
✅ The repo on GitHub contains source + docs but **not** `target/`.

---

## Recap — the order, and why each step comes when it does

| # | Step | Why here (not earlier/later) |
|---|------|------------------------------|
| 0 | Plan + prereqs | Can't build undefined scope with no tools |
| 1 | Scaffold (Initializr) | Standard layout + correct pom, instantly |
| 2 | Docker infra | App connects to DB/cache on startup → must exist first |
| 3 | Config + first boot | Prove wiring before writing features |
| 4a | Entity | Data foundation everything references |
| 4b | Repository | Needed by the service |
| 4c | Service (no cache) | Get logic right with one concept at a time |
| 4d | Controller → test CRUD | First end-to-end feature test |
| 4e | RedisConfig | Cache client must exist before the service uses it |
| 4f | Cache-aside → test cache | Add caching last, on proven code |
| 5 | Full test | Confirm the whole system |
| 6 | Docs + push | Share it |

## Common pitfalls (and the lesson behind each)
- **Ran the app right after Initializr → "Failed to configure a DataSource".** You added JPA but had no DB/config. *Lesson: bring up infra + config before the first run.*
- **Forgot the no-arg constructor on `Book`.** Hibernate/Jackson can't instantiate it. *Lesson: entities need a no-arg constructor.*
- **Put a class outside `com.meesho.library`.** Component scan never finds it → "no bean" errors. *Lesson: everything under the base package.*
- **Wrote all files, then ran once, then drowned in errors.** *Lesson: build in slices, verify each checkpoint.*
- **Cached but didn't evict on delete.** A later GET returns a deleted "ghost" book. *Lesson: keep cache consistent with the source of truth.*

---

### See also
- [`STUDY_ORDER.md`](STUDY_ORDER.md) — the order to *read* the finished project (top-down, to understand).
- [`FLOWS.md`](FLOWS.md) — diagrams of startup, wiring, and request flows.
- [`FUNDAMENTALS.md`](FUNDAMENTALS.md) — the *why* behind every concept you'll use above.
