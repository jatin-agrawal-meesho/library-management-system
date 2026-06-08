# HOW_TO_RUN

> The exact, ordered steps to start, test, and stop the whole system — **with the reason for each step**.
> Read this as "do X *because* Y", not just a list of commands. If a step fails, see **Troubleshooting** at the bottom.

The system is **three processes that must come up in order**:

```
   (1) Docker daemon            (2) Postgres + Redis containers       (3) the Spring Boot app
       the engine that      ───►    the two servers the app      ───►    connects to them as a
       runs containers              talks to (DB + cache)                 client on startup
```

Why this order? The app **connects to Postgres on startup** (it opens a connection pool and lets
Hibernate create the table). If Postgres isn't already up, the app fails to start. And the containers
can't run without a Docker daemon. So: daemon → containers → app.

---

## Prerequisites (one-time, already set up on this machine)

| Need | Why | This machine |
|------|-----|--------------|
| **JDK 21** | Compiles & runs the app; Spring Boot 3 needs Java 17+. `JAVA_HOME` is pinned to 21 so Maven doesn't use the wrong JDK. | ✅ pinned in `~/.zshrc` |
| **A Docker runtime** | `docker compose` needs a running daemon. macOS has no native daemon, so a VM provides one. | ✅ **Colima** (`/opt/homebrew/bin/colima`) |
| **Maven** | Builds/runs the project. We use the **wrapper** (`./mvnw`) so the exact pinned Maven version is used — no global install needed. | ✅ `./mvnw` in repo |

> If you ever use **Docker Desktop** or **OrbStack** instead of Colima, the only change is Step 1:
> just launch that app (it starts the daemon); skip `colima start`.

---

## Step 1 — Start the Docker daemon

```bash
colima start
```

**Why:** On macOS there is no built-in Docker daemon. Colima boots a tiny Linux VM and runs the daemon
inside it, then points your `docker` CLI at it (it creates/switches to the `colima` Docker *context*).
Without this, every `docker` command fails with *"Cannot connect to the Docker daemon"*.

**Verify:**
```bash
docker ps          # should print an (empty) table header, not a connection error
```

---

## Step 2 — Start Postgres + Redis

```bash
docker compose up -d
```

**Why:** This reads [`docker-compose.yml`](../docker-compose.yml) and launches the two servers the app
depends on — PostgreSQL (`:5432`) and Redis (`:6379`) — with the exact `library/library/library`
credentials that [`application.properties`](../src/main/resources/application.properties) expects.
`-d` = *detached* (run in the background and give the terminal back).

**What happens internally:** Docker pulls the `postgres:16` and `redis:7` images (first time only),
creates a private network and the `pgdata` volume, then starts both containers. Postgres, on its *first*
boot, also creates the empty `library` database.

**Verify (important — don't skip):**
```bash
docker ps                                            # both containers "Up"
docker exec library-postgres pg_isready -U library   # -> "accepting connections"
docker exec library-redis redis-cli ping             # -> PONG
```

**Why verify:** Postgres needs a few seconds to initialize on first boot. If the app connects *before*
Postgres is ready, startup fails with *"Connection refused"*. `pg_isready` tells you it's safe to proceed.

---

## Step 3 — Start the application

```bash
./mvnw spring-boot:run
```

**Why `./mvnw` and not `mvn`:** the wrapper uses the project's pinned Maven version, so the build is
reproducible on any machine without a global Maven install.

**Why `spring-boot:run`:** it compiles the sources and runs the app **straight from source** in one step
— ideal for development. (Alternative for a "production-like" run: `./mvnw clean package` then
`java -jar target/library-management-0.0.1-SNAPSHOT.jar` — same app, packaged as the executable fat jar.)

**What happens internally at startup** (see [`FLOWS.md`](FLOWS.md) for the full diagram):
1. `main()` calls `SpringApplication.run(...)`, which builds the Spring container (`ApplicationContext`).
2. **Auto-configuration** sees JPA + the Postgres driver on the classpath → creates a `DataSource`
   (HikariCP connection pool) and Hibernate; sees Redis → creates a `RedisConnectionFactory`.
3. **Component scan** finds `@RestController`, `@Service`, `@Configuration`, the repository interface,
   and **wires them together** via constructors (dependency injection).
4. **`ddl-auto=update`** makes Hibernate create/verify the `books` table from the `Book` entity.
5. Embedded **Tomcat starts on :8080**.

**You'll know it's ready when the log shows:**
```
HikariPool-1 - Start completed.
Tomcat started on port 8080 (http) with context path '/'
Started LibraryManagementApplication in 1.x seconds
```

> Run it in the foreground in its own terminal so you can **watch the logs** (the whole point — you'll
> see `CACHE HIT/MISS` and Hibernate SQL live). Stop it later with **Ctrl+C** in that terminal.

---

## Step 4 — Exercise the API (watch the logs while you do)

Keep the app's terminal visible. In a second terminal:

```bash
# Create books (POST). Each returns the row with its generated id.
curl -s -X POST localhost:8080/books -H 'Content-Type: application/json' \
     -d '{"title":"Clean Code","author":"Robert Martin"}'
curl -s -X POST localhost:8080/books -H 'Content-Type: application/json' \
     -d '{"title":"Effective Java","author":"Joshua Bloch"}'

curl -s localhost:8080/books          # list all

curl -s localhost:8080/books/1        # 1st time -> log: CACHE MISS + a Hibernate SELECT
curl -s localhost:8080/books/1        # 2nd time -> log: CACHE HIT, and NO SQL

curl -s -o /dev/null -w '%{http_code}\n' -X DELETE localhost:8080/books/1   # -> 204
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/books/1             # -> 404 (gone)
```

**Why this sequence:** it walks every endpoint and, crucially, calls `GET /books/1` **twice** so you see
the cache do its job — the first call pays for Postgres + fills Redis; the second is served from Redis
with no database query. That contrast is the lesson of the whole project.

---

## Step 5 — Peek inside Redis (optional but illuminating)

```bash
docker exec -it library-redis redis-cli
> KEYS *          # -> "book:1"   (only appears after a GET /books/1)
> GET book:1      # -> {"@class":"...Book","id":1,"title":"Clean Code","author":"Robert Martin"}
> TTL book:1      # -> ~600  (seconds left before it auto-expires; our TTL is 10 min)
> exit
```

**Why:** it makes the cache *concrete* — you can literally read the JSON we stored (readable thanks to
the serializers in [`RedisConfig`](../src/main/java/com/meesho/library/config/RedisConfig.java)) and watch
the TTL count down.

---

## Step 6 — Stop everything (reverse order)

Tear down in the **opposite** order you started — app first, then containers, then the daemon:

```bash
# 1) Stop the app:  press Ctrl+C in its terminal.
#    (If it's in the background, find and kill the LISTENER first — verify, THEN kill:)
lsof -iTCP:8080 -sTCP:LISTEN -t        # confirm this PID is the app before killing
kill $(lsof -iTCP:8080 -sTCP:LISTEN -t)

# 2) Stop Postgres + Redis:
docker compose down        # stops & removes the containers; KEEPS the pgdata volume (data survives)
# docker compose down -v   # ...use -v instead to ALSO delete the data (start fresh next time)

# 3) Stop the Docker VM:
colima stop
```

**Why reverse order:** stop the consumer (app) before the things it consumes (DB/cache), and stop the
daemon last since the containers live inside it.

**Why `down` vs `down -v`:** `down` leaves the `pgdata` volume, so your books are still there next run.
`-v` wipes it — use that when you want a clean slate.

> ⚠️ **Always filter for the LISTENER (`-sTCP:LISTEN`) before `kill`.** A plain `lsof -ti:8080` also
> matches *closed client sockets* (e.g. a leftover editor connection), and you could kill the wrong
> process. Verify the PID is actually the app, then kill.

---

## Quick reference (the happy path)

```bash
colima start                       # 1. daemon
docker compose up -d               # 2. Postgres + Redis
#   wait for: docker exec library-postgres pg_isready -U library
./mvnw spring-boot:run             # 3. app  (Ctrl+C to stop)
# ... use the API on localhost:8080 ...
docker compose down                # stop DB/cache (keep data)
colima stop                        # stop daemon
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Cannot connect to the Docker daemon` | Daemon not running | `colima start` (or launch Docker Desktop) |
| App startup fails: `Connection refused` / `HikariPool ... Exception` | Postgres not up yet / not started | Run Step 2; wait for `pg_isready` before Step 3 |
| App fails: `Port 8080 was already in use` | A previous app instance is still running | `kill $(lsof -iTCP:8080 -sTCP:LISTEN -t)`, then retry |
| `redis ... Unable to connect` at runtime | Redis container down | `docker compose up -d`; check `docker ps` |
| Build uses the wrong Java version | `JAVA_HOME` not pinned to 21 | Check `./mvnw -version`; ensure `JAVA_HOME` points to JDK 21 |
| Books vanished after restart | You ran `docker compose down -v` | That deletes the data volume; use plain `down` to keep data |

See also: [`FLOWS.md`](FLOWS.md) (what happens internally), [`REQUEST_FLOW.md`](REQUEST_FLOW.md)
(detailed per-endpoint traces), [`FUNDAMENTALS.md`](FUNDAMENTALS.md) (the concepts behind each step).






-------------------------------------------------------

(direct flow without why reasons)




Here's the exact walkthrough. Open two terminals — Terminal A runs the app (you'll watch its logs), Terminal B is where you test. All commands assume you're in the project root: cd ~/Desktop/Library-Management.

Terminal A — start the stack
Step 1 — Start the Docker engine (macOS has none built-in; Colima provides it)


colima start
✅ Check: docker ps prints an empty table (not a connection error).

Step 2 — Start Postgres + Redis, then wait until they're ready


docker compose up -d
docker exec library-postgres pg_isready -U library    # wait for: "accepting connections"
docker exec library-redis redis-cli ping              # expect: PONG
⚠️ Don't skip the wait — Postgres takes a few seconds on first boot, and the app will crash if it connects too early.

Step 3 — Run the app


./mvnw spring-boot:run
✅ It's ready when you see these three lines:


HikariPool-1 - Start completed.
Tomcat started on port 8080 (http) with context path '/'
Started LibraryManagementApplication in 1.x seconds
Leave this terminal open — this is where the CACHE HIT/MISS + SQL logs appear. (Stop it later with Ctrl+C.)

Terminal B — test the API
Step 4 — Create two books


curl -s -X POST localhost:8080/books -H 'Content-Type: application/json' \
     -d '{"title":"Clean Code","author":"Robert Martin"}'
curl -s -X POST localhost:8080/books -H 'Content-Type: application/json' \
     -d '{"title":"Effective Java","author":"Joshua Bloch"}'
Expect: {"id":1,...} and {"id":2,...}.

Step 5 — List all


curl -s localhost:8080/books
Expect a JSON array of both books.

Step 6 — The cache demo (the important part): call the same id twice


curl -s localhost:8080/books/1      # 1st call
curl -s localhost:8080/books/1      # 2nd call
👀 Now look at Terminal A. You should see:

1st call → CACHE MISS ... followed by a Hibernate SELECT (it hit the database)
2nd call → CACHE HIT ... with no SQL at all (served from Redis)
That MISS-then-HIT difference is the whole point of the project.

Step 7 — Delete, and confirm it's gone


curl -s -o /dev/null -w '%{http_code}\n' -X DELETE localhost:8080/books/1   # -> 204
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/books/1             # -> 404
In Terminal A you'll see DELETE ... evicted from Redis (a SELECT then DELETE — that's how deleteById works).

Step 8 — (optional) Peek inside Redis


docker exec library-redis redis-cli KEYS '*'      # -> "book:2"  (book:1 was evicted)
docker exec library-redis redis-cli GET book:2    # -> nil until you GET /books/2 once, then JSON
docker exec library-redis redis-cli TTL book:2    # -> seconds left (~600 = our 10-min TTL)
Stopping everything (reverse order)

# Terminal A: press Ctrl+C to stop the app
docker compose down     # stop Postgres + Redis (keeps your data; add -v to wipe it)
colima stop             # stop the Docker engine
That's the full loop. Full reference with the why behind each step is in HOW_TO_RUN.md, and the diagrams of what's happening internally are in FLOWS.md.

