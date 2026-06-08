package com.meesho.library.repository;

import com.meesho.library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The REPOSITORY: the data-access layer for Book.
 *
 * <p>Notice there is NO implementation here - it is an interface with no methods
 * of its own. At startup, Spring Data JPA finds every interface that extends
 * {@link JpaRepository}, generates a proxy class that implements it (writing the
 * actual JPA/SQL calls for us), and registers that proxy as a Spring bean.
 *
 * <p>{@code JpaRepository<Book, Long>} reads as: "a repository of Book entities
 * whose primary-key type is Long." From those two type parameters alone we inherit
 * a full CRUD API:
 * <ul>
 *   <li>{@code save(book)}          - INSERT or UPDATE</li>
 *   <li>{@code findById(id)}        - SELECT by primary key (returns Optional&lt;Book&gt;)</li>
 *   <li>{@code findAll()}           - SELECT all rows</li>
 *   <li>{@code deleteById(id)}      - DELETE by primary key</li>
 *   <li>{@code count()}, {@code existsById(id)}, ...</li>
 * </ul>
 *
 * <p>For this minimal project the inherited methods are all we need, so the body
 * stays empty. (Later you could add a "derived query" like
 * {@code List<Book> findByAuthor(String author);} and Spring would generate the
 * SQL from the method name - but we are keeping it simple.)
 */
public interface BookRepository extends JpaRepository<Book, Long> {
    // Intentionally empty: all CRUD methods are inherited and implemented for us.
}
