package com.example.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory implementation of {@link BookRepository}. */
public class InMemoryBookRepository implements BookRepository {

    private final Map<String, Book> booksByIsbn = new LinkedHashMap<>();

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        return Optional.ofNullable(booksByIsbn.get(isbn));
    }

    @Override
    public List<Book> findAll() {
        return new ArrayList<>(booksByIsbn.values());
    }

    @Override
    public void save(Book book) {
        booksByIsbn.put(book.isbn(), book);
    }

    public int size() {
        return booksByIsbn.size();
    }
}
