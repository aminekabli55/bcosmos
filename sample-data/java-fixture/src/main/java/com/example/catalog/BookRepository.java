package com.example.catalog;

import java.util.List;
import java.util.Optional;

/** Storage contract for books. */
public interface BookRepository {

    Optional<Book> findByIsbn(String isbn);

    List<Book> findAll();

    void save(Book book);
}
