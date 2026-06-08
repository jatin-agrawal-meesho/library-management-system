# LEARNING_NOTES

> An **append-only log**. Every new annotation, class, dependency, command, or framework feature
> introduced in the project gets a short entry here, in the phase it was added. Newest phase at the
> bottom. Use this as a quick "what does this thing do again?" index.

---

## Phase 1 — Project setup & startup

### Environment / tooling

- **JDK 21 (OpenJDK)** — the Java Development Kit used to compile & run. Includes `javac` (compiler),
  `java` (launcher), and the JVM. Chosen because Spring Boot 3.x needs Java 17+ and 21 is current LTS.
- **`JAVA_HOME`** — env var pointing at the JDK to use. Pinned to JDK 21 in `~/.zshrc` so Maven does
  not accidentally use JDK 26 (pulled in as a Homebrew dependency of Maven). New tools read this var.
- **Maven 3.9.16** — the build tool (dependency management + build lifecycle). Equivalent to npm + make.
- **Maven Wrapper (`mvnw`)** — runs a pinned Maven version without a global install; generated via
  `mvn wrapper:wrapper`.

### Maven concepts

- **`pom.xml`** — the build descriptor (deps, Java version, plugins). Maven's `package.json`.
- **Coordinates** — `groupId:artifactId:version`, a unique artifact id.
- **Parent POM (`spring-boot-starter-parent`)** — inherited; provides a **BOM** (curated, compatible
  dependency versions) so our own dependencies omit `<version>`. Holds the Spring Boot version (3.4.5).
- **Maven Central** — public library registry; downloads cached in `~/.m2/repository`.
- **Dependency `scope`** — `test` scope = library available only when compiling/running tests, not shipped.

### Dependencies added

| Dependency | Why |
|------------|-----|
| `spring-boot-starter-web` | Bundles Spring MVC + embedded Tomcat + Jackson (JSON). Makes it a web app. |
| `spring-boot-starter-test` (scope `test`) | JUnit 5, Spring test support, Mockito — for future tests. |
| `spring-boot-maven-plugin` (build plugin) | Enables `mvn spring-boot:run` and builds the executable fat jar. |

### Annotations

- **`@SpringBootApplication`** — master annotation on the main class. Bundles three:
  - `@SpringBootConfiguration` — class is a source of bean definitions.
  - `@EnableAutoConfiguration` — turns on classpath-driven auto-configuration (→ starts Tomcat).
  - `@ComponentScan` — scans `com.meesho.library` (+ sub-packages) for components to register as beans.

### Classes / API

- **`LibraryManagementApplication`** — the entry-point class (holds `main`).
- **`SpringApplication.run(SourceClass.class, args)`** — boots the Spring `ApplicationContext`: runs
  auto-configuration, scans/wires beans (DI), and starts the embedded web server.

### Framework concepts (defined in FUNDAMENTALS.md, listed here for the index)

- **IoC / DI** — the framework (not your code) creates objects ("beans") and injects their dependencies.
- **Bean** — an object managed by the Spring container.
- **ApplicationContext** — the Spring container holding all beans.
- **Auto-configuration** — Spring Boot configuring sensible defaults based on what's on the classpath.
- **Embedded server (Tomcat)** — a web server bundled inside the app jar; no external server to deploy to.
- **Front controller / `DispatcherServlet`** — the single servlet all HTTP requests pass through
  (see REQUEST_FLOW.md).

### Configuration keys used (`application.properties`)

- `spring.application.name=library-management` — app name shown in logs/tooling.
- `server.port=8080` — port the embedded Tomcat listens on.

### Useful commands learned

| Command | Effect |
|---------|--------|
| `mvn clean package` | Delete `target/`, compile, run tests, build the executable jar. |
| `mvn spring-boot:run` | Run the app straight from source (no jar step). |
| `java -jar target/library-management-0.0.1-SNAPSHOT.jar` | Run the built executable fat jar. |
| `./mvnw <goal>` | Same as `mvn`, but uses the project's pinned Maven (no global install needed). |
| `mvn -version` | Show Maven + the JDK Maven is running on (used to catch the JDK 21 vs 26 issue). |

### Verified outcome

App boots in ~0.6s; embedded Tomcat listens on `:8080`; `GET /` returns a clean `404` (no endpoints yet,
which is the correct Phase 1 result — infrastructure proven, see REQUEST_FLOW.md).

---

## Phase 2 — Book CRUD with PostgreSQL (JPA/Hibernate) + Redis cache

> Format for each item: **what it is · why it exists · how it works internally · the one-liner you'd say in an interview.**

### Dependencies added

#### `spring-boot-starter-data-jpa`
- **What:** a starter bundling the JPA API, Hibernate, HikariCP (connection pool), and Spring Data JPA.
- **Why:** so we can map Java objects to tables and get repositories without writing SQL/JDBC.
- **Internal:** its presence on the classpath triggers Spring Boot auto-configuration to build a `DataSource`, an `EntityManagerFactory` (Hibernate), and a `JpaTransactionManager` from the `spring.datasource.*` / `spring.jpa.*` properties.
- **Interview:** "It's the umbrella dependency that turns on JPA persistence in a Spring Boot app — JPA spec + Hibernate + connection pool + Spring Data, all version-aligned by the BOM."

#### `postgresql` (JDBC driver, scope `runtime`)
- **What:** the PostgreSQL JDBC driver — the code that speaks Postgres's wire protocol over a socket.
- **Why:** Hibernate/JDBC need a concrete driver to actually reach the database.
- **Internal:** auto-detected from the `jdbc:postgresql://` URL prefix; scope `runtime` because our code never imports it — only the runtime does.
- **Interview:** "JDBC is the standard API; the driver is the vendor-specific implementation for a given database."

#### `spring-boot-starter-data-redis`
- **What:** starter bundling the Lettuce Redis client and `RedisTemplate`.
- **Why:** to use Redis as a cache.
- **Internal:** its presence auto-configures a `RedisConnectionFactory` (Lettuce) from `spring.data.redis.*`.
- **Interview:** "Spring's abstraction over a Redis client; `RedisTemplate` is the main entry point for operations."

### Annotations

#### `@Entity`
- **What:** marks a class as a JPA persistent entity (one instance ⇄ one table row).
- **Why:** lets the ORM map the class to a table instead of you writing SQL DDL/DML.
- **Internal:** Hibernate scans for `@Entity` types at startup and registers a metamodel (class→table, fields→columns). With `ddl-auto=update` it also creates/alters the table.
- **Interview:** "It declares a domain class as something the persistence provider manages and maps to a relational table."

#### `@Table(name = "books")`
- **What:** overrides the default table name (which would be the class name `book`).
- **Why:** we want the table called `books`.
- **Internal:** read by Hibernate when building the metamodel and generating SQL.
- **Interview:** "Optional — only needed when the table name differs from the entity name."

#### `@Id` and `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- **What:** `@Id` = the primary key field; `@GeneratedValue(IDENTITY)` = let the database generate it.
- **Why:** every row needs a unique key; IDENTITY delegates generation to Postgres's auto-increment/identity column.
- **Internal:** Hibernate omits the id from the `INSERT`, lets Postgres assign it, then reads the generated value back into the object.
- **Interview:** "IDENTITY relies on the DB's auto-increment; alternatives are SEQUENCE and TABLE. IDENTITY can't batch inserts because it needs the id immediately."

#### `@Configuration` + `@Bean`
- **What:** `@Configuration` = a class that declares beans; `@Bean` = a method whose return value is registered as a bean.
- **Why:** to create beans that need custom construction (our `RedisTemplate`), which can't come from a simple stereotype annotation.
- **Internal:** at startup Spring calls each `@Bean` method once and stores the result as a singleton in the `ApplicationContext`. (`@Configuration` classes are CGLIB-proxied so inter-bean method calls return the same singleton.)
- **Interview:** "Java-based configuration — the modern replacement for XML bean definitions."

#### `@Service`
- **What:** a stereotype marking a business-logic component; a specialization of `@Component`.
- **Why:** to register the class as a bean and signal its role (logic layer).
- **Internal:** found by component scan, instantiated once, dependencies injected.
- **Interview:** "Functionally like `@Component`; semantically it marks the service layer (and is a hook for things like exception translation)."

#### `@RestController`
- **What:** `@Controller` + `@ResponseBody` — a controller whose method return values become the HTTP response body.
- **Why:** building a JSON REST API, not server-rendered HTML.
- **Internal:** component-scanned into a bean; method-level mappings registered with `RequestMappingHandlerMapping`; return values serialized by an `HttpMessageConverter` (Jackson).
- **Interview:** "It's `@Controller` with `@ResponseBody` applied to every method — returns data, not view names."

#### `@RequestMapping` / `@GetMapping` / `@PostMapping` / `@DeleteMapping`
- **What:** map an HTTP (method, path) to a handler method. `@RequestMapping("/books")` at class level sets the base path; the verb-specific ones are shortcuts.
- **Why:** declarative routing.
- **Internal:** at startup Spring builds a table of (method,path)→handler; the `DispatcherServlet` consults it (`HandlerMapping`) per request.
- **Interview:** "`@GetMapping` is shorthand for `@RequestMapping(method=GET)`. Class-level + method-level paths concatenate."

#### `@RequestBody`
- **What:** binds and deserializes the HTTP request body into a method parameter.
- **Why:** to turn incoming JSON into a `Book` object.
- **Internal:** Spring picks a matching `HttpMessageConverter` (Jackson for JSON) based on the `Content-Type` and the parameter type.
- **Interview:** "Deserializes the body via a message converter; pair it with `@Valid` when you want validation."

#### `@PathVariable`
- **What:** binds a URI template segment (`/books/{id}`) to a method parameter.
- **Why:** to read the `id` from the URL.
- **Internal:** Spring extracts the segment during handler mapping and converts the string to the parameter type (`Long`).
- **Interview:** "Maps a part of the path into an argument; `@RequestParam` does the same for query string params."

#### `@ResponseStatus(...)`
- **What:** sets the HTTP status code for the response (we use `201 CREATED` and `204 NO_CONTENT`).
- **Why:** correct REST semantics — created vs. no-content.
- **Internal:** applied by Spring MVC when writing the response; also works on exceptions.
- **Interview:** "A declarative way to set the status; for dynamic status use `ResponseEntity` instead."

### Interfaces / framework features

#### `JpaRepository<T, ID>` (Spring Data JPA)
- **What:** a generic interface giving full CRUD + paging/sorting for entity `T` with key `ID`.
- **Why:** eliminates boilerplate data-access code — you declare an interface, not an implementation.
- **Internal:** Spring Data creates a runtime **proxy** implementing your interface; each call is routed to a shared `SimpleJpaRepository` backed by the JPA `EntityManager`. **Derived queries** (e.g. `findByAuthor`) are parsed from the method name into JPQL/SQL.
- **Interview:** "You define an interface; Spring Data generates the implementation at runtime. Method names can declare queries; `@Query` lets you write JPQL/SQL explicitly."

#### `RedisTemplate<K, V>` and `opsForValue()`
- **What:** the main Spring abstraction for Redis operations; `opsForValue()` exposes simple string/value `GET`/`SET`.
- **Why:** a typed, serializer-aware client so we read/write `Book` objects, not raw bytes.
- **Internal:** delegates to the Lettuce connection; uses the key/value **serializers** we configured (String + JSON). `set(key, value, ttl)` issues `SET key value EX <seconds>`.
- **Interview:** "`RedisTemplate` is the general client; `opsForValue/opsForHash/opsForList...` map to Redis data types. Serializer choice decides how objects are stored."

#### Constructor injection (no `@Autowired`)
- **What:** dependencies declared as constructor parameters and set once.
- **Why:** immutable, fully-initialized, easily testable beans; preferred over field injection.
- **Internal:** with a single constructor, Spring auto-selects it and supplies matching beans — `@Autowired` is implicit.
- **Interview:** "Constructor injection is recommended because it makes dependencies explicit and mandatory and allows `final` fields; field injection hides dependencies and breaks easy unit testing."

#### Cache-aside pattern (the design, not an annotation)
- **What:** application-managed caching — check cache, on miss read DB and populate cache; evict on write.
- **Why:** cut load and latency on hot reads (here, `GET /books/{id}`).
- **Internal:** implemented manually in `BookService.getById/delete` so the hit/miss path is explicit and loggable. (Spring also offers a declarative version via `@Cacheable`/`@CacheEvict`, deliberately not used here so the flow stays visible.)
- **Interview:** "Cache-aside (lazy loading): the app owns the cache. Main risk is staleness — handle it with eviction on writes and a TTL. Alternatives: write-through, write-behind, read-through."

### Configuration keys added (`application.properties`)
- `spring.datasource.url / username / password` — how to reach PostgreSQL.
- `spring.jpa.hibernate.ddl-auto=update` — Hibernate creates/alters tables from entities at startup.
- `spring.jpa.show-sql=true` + `spring.jpa.properties.hibernate.format_sql=true` — print readable SQL.
- `spring.data.redis.host / port` — how to reach Redis.
- `logging.level.com.meesho.library=INFO` — ensure our cache hit/miss logs appear.

### Useful commands learned

| Command | Effect |
|---------|--------|
| `docker compose up -d` | Start PostgreSQL + Redis containers in the background. |
| `docker compose down` | Stop them (Postgres data persists in its volume). |
| `docker compose down -v` | Stop them **and delete** the Postgres data volume. |
| `docker exec -it library-redis redis-cli` | Open a Redis shell to inspect cache keys/values. |
| `redis-cli KEYS '*'` / `GET book:1` | List cache keys / read a cached Book (JSON). |
| `curl -X POST localhost:8080/books -H 'Content-Type: application/json' -d '{...}'` | Create a book. |

### Verified outcome (expected)
First `GET /books/{id}` logs `CACHE MISS` + a Hibernate `select`; the repeat logs only `CACHE HIT` with
**no** SQL — the cache demonstrably serving reads. `DELETE` removes the row and evicts the key. See
`REQUEST_FLOW.md` §3–§5.
