package com.meesho.library.controller;

import com.meesho.library.entity.Book;
import com.meesho.library.service.BookService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The CONTROLLER: the HTTP layer for books.
 *
 * <p>{@code @RestController} = {@code @Controller} (a {@code @Component}, so the
 * component scan registers it as a bean) + {@code @ResponseBody} (whatever a
 * method returns is serialized straight to the HTTP response body as JSON by
 * Jackson - we are building a REST API, not rendering HTML pages).
 *
 * <p>{@code @RequestMapping("/books")} sets the base path; the method-level
 * mappings below add the verb and any sub-path. Each mapping is registered with
 * Spring MVC's HandlerMapping at startup so the DispatcherServlet can route to it.
 *
 * <p>This class contains NO business logic - it only:
 * <ol>
 *   <li>declares which (HTTP method, path) each method handles,</li>
 *   <li>binds the request (JSON body / path variable) into Java parameters,</li>
 *   <li>delegates to {@link BookService}, and returns the result (serialized to JSON).</li>
 * </ol>
 */
@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService bookService;

    // Single constructor -> Spring injects the BookService bean automatically.
    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /**
     * POST /books
     * {@code @RequestBody} tells Jackson to deserialize the JSON request body into
     * a Book object. {@code @ResponseStatus(CREATED)} returns 201 instead of 200.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Book create(@RequestBody Book book) {
        return bookService.create(book);
    }

    /** GET /books -> return the full list (serialized to a JSON array). */
    @GetMapping
    public List<Book> getAll() {
        return bookService.getAll();
    }

    /**
     * GET /books/{id}
     * {@code @PathVariable} binds the {id} segment of the URL to the method
     * parameter. This is the cached endpoint (see BookService.getById).
     */
    @GetMapping("/{id}")
    public Book getById(@PathVariable Long id) {
        return bookService.getById(id);
    }

    /**
     * DELETE /books/{id}
     * Returns 204 No Content on success (nothing to send back).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        bookService.delete(id);
    }
}
