package com.meesho.library.service;

import com.meesho.library.entity.Book;
import com.meesho.library.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

/**
 * The SERVICE layer: business logic for Book.
 *
 * <p>{@code @Service} marks this class as a Spring bean (a specialized
 * {@code @Component}). Component scanning finds it, the IoC container creates ONE
 * instance, and injects its dependencies through the constructor below.
 *
 * <p>For GET-by-id this class implements the <b>cache-aside</b> pattern:
 * <ol>
 *   <li>look in Redis first;</li>
 *   <li>on a miss, read PostgreSQL and then write the result into Redis;</li>
 *   <li>on a hit, return immediately and skip the database entirely.</li>
 * </ol>
 * The log lines below let you SEE which path each request took.
 */
@Service
public class BookService {

    // SLF4J logger - the standard logging facade in Spring apps. The logs from
    // this class are visible because application.properties sets
    // logging.level.com.meesho.library=INFO.
    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    // How long a cached book lives before Redis auto-expires it.
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    // Dependencies. final = set once in the constructor and never reassigned.
    private final BookRepository bookRepository;
    private final RedisTemplate<String, Book> redisTemplate;

    /**
     * Constructor injection. Because there is exactly ONE constructor, Spring
     * automatically supplies these beans - no @Autowired annotation needed.
     * This is the preferred DI style: dependencies are explicit and the object
     * is fully initialized (and testable) the moment it is created.
     */
    public BookService(BookRepository bookRepository, RedisTemplate<String, Book> redisTemplate) {
        this.bookRepository = bookRepository;
        this.redisTemplate = redisTemplate;
    }

    /** POST /books -> persist a new book. We do NOT cache on create; the cache
     *  fills lazily on the first GET-by-id. */
    public Book create(Book book) {
        Book saved = bookRepository.save(book);
        log.info("CREATE     -> saved book id={} to PostgreSQL", saved.getId());
        return saved;
    }

    /** GET /books -> list all books. Not cached (only GET-by-id is cached). */
    public List<Book> getAll() {
        log.info("LIST       -> fetching ALL books from PostgreSQL");
        return bookRepository.findAll();
    }

    /**
     * GET /books/{id} -> the cache-aside read path.
     *
     * <p>This is the method that demonstrates cache MISS vs cache HIT.
     */
    public Book getById(Long id) {
        String key = cacheKey(id);

        // 1) Ask Redis first.
        Book cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            // CACHE HIT: served from memory, no database round-trip.
            log.info("CACHE HIT  -> book id={} served from REDIS (no DB query)", id);
            return cached;
        }

        // 2) CACHE MISS: Redis didn't have it, so go to PostgreSQL.
        log.info("CACHE MISS -> book id={} not in Redis; querying POSTGRES", id);
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Book not found: id=" + id));

        // 3) Populate the cache so the NEXT request for this id is a hit.
        redisTemplate.opsForValue().set(key, book, CACHE_TTL);
        log.info("CACHE FILL -> book id={} loaded from Postgres and written to Redis (TTL {} min)",
                id, CACHE_TTL.toMinutes());

        return book;
    }

    /**
     * DELETE /books/{id} -> remove from PostgreSQL AND evict from Redis.
     *
     * <p>Evicting is essential: if we deleted the row but left the cache entry,
     * a later GET would be a cache HIT and return a "ghost" book that no longer
     * exists in the database. Keeping cache and source-of-truth consistent is the
     * hardest part of caching in real systems.
     */
    public void delete(Long id) {
        bookRepository.deleteById(id);
        Boolean evicted = redisTemplate.delete(cacheKey(id));
        log.info("DELETE     -> removed book id={} from Postgres; evicted from Redis (was cached: {})",
                id, evicted);
    }

    /** Builds the Redis key for a book, e.g. id=1 -> "book:1". A namespaced,
     *  predictable key scheme keeps cache entries easy to find and reason about. */
    private String cacheKey(Long id) {
        return "book:" + id;
    }
}
