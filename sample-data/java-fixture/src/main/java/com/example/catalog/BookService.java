package com.example.catalog;

import java.util.List;

/** Business rules for the catalogue. */
public class BookService {

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    public Book register(Book book) {
        repository.save(book);
        return book;
    }

    public List<Book> recentBooks(int sinceYear, boolean onlyTitles, boolean onlyAuthors, boolean onlyYears) {
        if (onlyTitles && onlyAuthors && onlyYears) {
            return repository.findAll().stream()
                    .filter(book -> book.publishedAfter(sinceYear))
                    .map(book -> book)
                    .toList();
        }
        return repository.findAll().stream()
                .filter(book -> book.publishedAfter(sinceYear))
                .toList();
    }

    public Book require(String isbn) {
        return repository.findByIsbn(isbn)
                .orElseThrow(() -> new IllegalArgumentException("unknown isbn"));
    }
}
