package com.meesho.library.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The Book ENTITY.
 *
 * <p>A plain Java object (POJO) that JPA/Hibernate maps to a row in the
 * "books" table. The annotations are metadata that tell Hibernate HOW to do
 * that mapping; the class itself stays an ordinary class with fields + getters.
 *
 * <p>Note the package: {@code jakarta.persistence.*} (NOT {@code javax.persistence}).
 * Spring Boot 3 moved from Java EE (javax) to Jakarta EE (jakarta). Old tutorials
 * use javax and will not compile here.
 *
 * <p>This is NOT a Spring bean: the IoC container makes ONE shared instance of a
 * service/controller, but there are MANY Book objects (one per row), so Hibernate
 * - not Spring - manages their lifecycle.
 */
@Entity                       // "Hibernate, persist instances of this class as table rows."
@Table(name = "books")        // map to a table literally named "books" (default would be "book").
public class Book {

    /**
     * Primary key. @GeneratedValue(IDENTITY) means the DATABASE generates the id
     * (Postgres uses an auto-increment identity column); we leave it null on insert
     * and Hibernate fills it in from the value the DB assigned.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No column annotations needed: by convention Hibernate maps these to
    // columns named "title" and "author" (varchar). We keep it minimal.
    private String title;
    private String author;

    /**
     * No-argument constructor. REQUIRED by JPA: Hibernate instantiates the object
     * via reflection when reading a row back, then sets the fields. Jackson (JSON)
     * uses it the same way when deserializing a request body.
     */
    public Book() {
    }

    public Book(String title, String author) {
        this.title = title;
        this.author = author;
    }

    // Getters/setters: Hibernate and Jackson use these to read/write field values.
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
