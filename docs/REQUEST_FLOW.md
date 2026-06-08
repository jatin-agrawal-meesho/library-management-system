# REQUEST_FLOW

> The complete lifecycle of an HTTP request through this application.
>
> **Phase 2 status:** the four book endpoints now exist. The infrastructure section below
> (front controller / `DispatcherServlet`) still applies to every request; after it you'll
> find a concrete step-by-step trace for **each** endpoint — `POST /books`, `GET /books`,
> `GET /books/{id}` (both the **cache miss** and **cache hit** paths), and `DELETE /books/{id}` —
> covering the full `Client → Spring Boot → Controller → Service → Repository → Hibernate →
> PostgreSQL → Redis → Response` chain.

---

## The mental model: the "Front Controller" pattern

Spring MVC (pulled in by `spring-boot-starter-web`) routes **every** incoming HTTP request through a
single servlet called the **`DispatcherServlet`**. It is the "front controller": one entry point that
inspects each request and dispatches it to the right handler. You never write it — Spring Boot
auto-configures and registers it during startup.

```
        HTTP request
             │
             ▼
   ┌───────────────────┐     embedded server: accepts the TCP connection,
   │  Tomcat (port 8080)│     parses raw bytes into an HttpServletRequest
   └─────────┬─────────┘
             ▼
   ┌───────────────────────┐  the ONE front controller for all requests
   │   DispatcherServlet    │
   └─────────┬─────────────┘
             ▼
   ┌───────────────────────┐  "which method handles this (method, path)?"
   │   HandlerMapping       │
   └─────────┬─────────────┘
             ▼
        handler found? ──── no ──►  404 Not Found  (← Phase 1 lands here for every path)
             │ yes
             ▼
   ┌───────────────────────┐  invokes your @RestController method, converts the
   │   Controller method    │  return value to JSON (Jackson), writes the response
   └───────────────────────┘
```

---

## Phase 1 — the only flow that exists today: `GET /` → `404`

Concrete trace of the request we actually sent during startup verification:

1. **Client → Tomcat.** `curl` opened a TCP connection to `localhost:8080` and sent
   `GET / HTTP/1.1`. The **embedded Tomcat** (started by auto-configuration) accepted the connection.
2. **Tomcat → request object.** Tomcat parsed the raw HTTP bytes into a Java `HttpServletRequest`
   object and handed it to the registered servlet.
3. **DispatcherServlet receives it.** As the front controller mapped to `/*`, it received the request
   and asked the **`HandlerMapping`**: *"is there a handler method for `GET /`?"*
4. **No handler found.** We have written no controllers, so there is no mapping for `/` (nor any path).
5. **404 produced.** Spring Boot's default error handling produced a structured response:
   ```json
   {"timestamp":"2026-06-07T15:07:19.728+00:00","status":404,"error":"Not Found","path":"/"}
   ```
6. **Response → client.** Tomcat serialized that back over the connection. `curl` reported `404`.

**Why a 404 is the *correct* Phase 1 outcome:** it proves steps 1–3 work end-to-end — the server
listens, accepts connections, and the dispatcher is wired in. The only thing missing is a handler,
which is exactly what we have not built yet. A connection error (not a 404) would mean the server
never started; a 500 would mean it started but crashed handling the request. We got the clean,
expected 404.

---

## The shape every book request follows

Now that controllers exist, the generic path for a book request is:

```
### <METHOD> <path>          e.g. POST /books

1. Tomcat accepts the connection and builds the HttpServletRequest.
2. DispatcherServlet → HandlerMapping resolves it to BookController.<method>().
3. (POST/PUT) HttpMessageConverter (Jackson) deserializes the JSON body into a Java object (DTO/entity).
4. (Optional) Bean Validation runs (@Valid).
5. Controller method calls the Service layer (business logic).
6. Service calls the Repository layer (data access; JPA/Hibernate → SQL → PostgreSQL).
7. Result bubbles back up; Jackson serializes the return value to a JSON response body.
8. DispatcherServlet sets the HTTP status; Tomcat writes the response to the client.
```

### Endpoints in this project

| Method & path      | Purpose              | Cached? | Status |
|--------------------|----------------------|---------|--------|
| `POST /books`      | Create a book        | no      | ✅ done |
| `GET /books`       | List all books       | no      | ✅ done |
| `GET /books/{id}`  | Fetch one book by id | **yes** | ✅ done |
| `DELETE /books/{id}`| Delete a book       | evicts  | ✅ done |

> Every flow below starts the same way (Tomcat → DispatcherServlet → HandlerMapping → the
> `BookController` method). For brevity that prefix is written once here and referenced as
> **"[dispatch to `BookController.xxx`]"** in each trace.

---

## 1. `POST /books` — create a book

**Request:** `POST /books` with JSON body `{"title":"Clean Code","author":"Robert Martin"}`

```
Client ──JSON──► Controller ──Book──► Service ──save──► Repository ──► Hibernate ──INSERT──► PostgreSQL
                                                                                              │ returns id
   ◄──────────────── JSON {id,title,author} ◄───────────── saved Book ◄──────────────────────┘
                                              (Redis NOT touched on create)
```

1. **[dispatch to `BookController.create`]** — HandlerMapping matches `POST /books`.
2. **JSON → object.** `@RequestBody` makes Spring invoke Jackson (`HttpMessageConverter`), which deserializes the request body into a `Book` (id is null; only title/author set).
3. **Controller → Service.** `bookService.create(book)`.
4. **Service → Repository.** `bookRepository.save(book)`. Because `id` is null, Hibernate treats it as a new row.
5. **Hibernate → SQL.** Generates `insert into books (author, title) values (?, ?)` and runs it via the JDBC driver. Postgres assigns the identity `id` and returns it; Hibernate sets it on the `Book`. *(Cache is intentionally not populated on create.)*
6. **Back up.** The saved `Book` (now with `id`) returns to the controller. `@ResponseStatus(CREATED)` sets **201**, Jackson serializes the `Book` to JSON, Tomcat writes the response.

**Log you'll see:** `CREATE -> saved book id=1 to PostgreSQL` (plus Hibernate's `insert` SQL).

---

## 2. `GET /books` — list all books

**Request:** `GET /books`

```
Client ──► Controller ──► Service ──findAll──► Repository ──► Hibernate ──SELECT *──► PostgreSQL
   ◄──── JSON array ◄──── List<Book> ◄─────────────────────────────────────────────────┘
                                          (Redis NOT touched — list is not cached)
```

1. **[dispatch to `BookController.getAll`]** — HandlerMapping matches `GET /books`.
2. **Controller → Service.** `bookService.getAll()`.
3. **Service → Repository.** `bookRepository.findAll()`.
4. **Hibernate → SQL.** `select b.id, b.author, b.title from books b`; rows are hydrated into `Book` objects.
5. **Back up.** `List<Book>` → Jackson serializes to a JSON array → **200 OK**.

**Log you'll see:** `LIST -> fetching ALL books from PostgreSQL`.

---

## 3. `GET /books/{id}` — **CACHE MISS** (first request for this id)

**Request:** `GET /books/1` when `book:1` is **not** in Redis.

```
                         (1) check Redis
Client ──► Controller ──► Service ───────────► Redis: GET "book:1"  ──► (nil)   ← MISS
                            │
                            │ (2) fall back to DB
                            ▼
                         Repository ──► Hibernate ──SELECT──► PostgreSQL ──► row
                            │
                            │ (3) populate cache
                            ▼
                         Redis: SET "book:1" = {Book as JSON}  EX 600
                            │
   ◄──────── JSON Book ◄────┘   (200 OK)
```

1. **[dispatch to `BookController.getById`]** — `@PathVariable Long id` binds `1` from the URL.
2. **Controller → Service.** `bookService.getById(1L)`.
3. **Check Redis first.** `redisTemplate.opsForValue().get("book:1")` → returns `null`. **This is the cache miss.**
4. **Fall back to PostgreSQL.** `bookRepository.findById(1L)` → Hibernate runs `select ... from books where id=?` → row found → hydrated into a `Book`. *(If no row: throws `ResponseStatusException(404)` and the client gets a clean 404.)*
5. **Populate the cache.** `redisTemplate.opsForValue().set("book:1", book, 10 min)` writes the Book as JSON with a TTL, so the next request is a hit.
6. **Back up.** `Book` → JSON → **200 OK**.

**Logs you'll see (in order):**
```
CACHE MISS -> book id=1 not in Redis; querying POSTGRES
CACHE FILL -> book id=1 loaded from Postgres and written to Redis (TTL 10 min)
```
(plus Hibernate's `select` SQL — proof the DB *was* queried this time)

---

## 4. `GET /books/{id}` — **CACHE HIT** (any repeat request within the TTL)

**Request:** `GET /books/1` again, while `book:1` is still in Redis.

```
Client ──► Controller ──► Service ──► Redis: GET "book:1" ──► {Book as JSON}  ← HIT
                            │
   ◄──── JSON Book ◄────────┘   (200 OK)        ✗ PostgreSQL is NEVER contacted
                                                ✗ Hibernate emits NO SQL
```

1. **[dispatch to `BookController.getById`]**.
2. **Controller → Service.** `bookService.getById(1L)`.
3. **Check Redis first.** `redisTemplate.opsForValue().get("book:1")` → returns the cached `Book` (deserialized from JSON). **This is the cache hit.**
4. **Return immediately.** The method returns the cached `Book` — **the repository, Hibernate, and Postgres are never touched.**
5. **Back up.** `Book` → JSON → **200 OK**.

**Log you'll see (single line, and NO Hibernate SQL):**
```
CACHE HIT -> book id=1 served from REDIS (no DB query)
```

> **The whole point of Phase 2 in two log lines:** the *first* `GET /books/1` prints `CACHE MISS` **and** a SQL `select`; the *second* prints only `CACHE HIT` with **no SQL**. That visible difference is the cache working.

---

## 5. `DELETE /books/{id}` — delete + evict

**Request:** `DELETE /books/1`

```
Client ──► Controller ──► Service ──┬── Repository ──► Hibernate ──DELETE──► PostgreSQL
                                    │
                                    └── Redis: DEL "book:1"   (evict, so no ghost reads)
   ◄──── 204 No Content ◄───────────┘
```

1. **[dispatch to `BookController.delete`]** — `@PathVariable` binds `id=1`.
2. **Controller → Service.** `bookService.delete(1L)`.
3. **Delete from source of truth.** `bookRepository.deleteById(1L)`. Spring Data's `deleteById` is implemented as `findById(id).ifPresent(::delete)`, so Hibernate actually emits **two** statements — first `select ... from books where id=?` (to load the entity), then `delete from books where id=?`. (Verified in the live logs.)
4. **Evict from cache.** `redisTemplate.delete("book:1")` removes the cached copy. **Essential:** skip this and a later GET would be a HIT returning a deleted "ghost" book.
5. **Back up.** Nothing to return → `@ResponseStatus(NO_CONTENT)` → **204**.

**Log you'll see:** `DELETE -> removed book id=1 from Postgres; evicted from Redis (was cached: true)`.

---

## How to watch it yourself

With the app running (and Postgres + Redis up via `docker compose up -d`):

```bash
# create
curl -s -X POST localhost:8080/books -H 'Content-Type: application/json' \
     -d '{"title":"Clean Code","author":"Robert Martin"}'

curl -s localhost:8080/books/1     # 1st call -> CACHE MISS + SQL select in the app logs
curl -s localhost:8080/books/1     # 2nd call -> CACHE HIT, no SQL

# peek inside Redis directly:
docker exec -it library-redis redis-cli KEYS '*'      # -> "book:1"
docker exec -it library-redis redis-cli GET book:1    # -> the Book as JSON

curl -s -X DELETE localhost:8080/books/1   # deletes row + evicts cache (204)
docker exec -it library-redis redis-cli GET book:1    # -> (nil) again
```

Keep an eye on the **application console** while running these — the `CACHE HIT/MISS` lines and Hibernate's SQL are exactly the internal flow this document describes.
