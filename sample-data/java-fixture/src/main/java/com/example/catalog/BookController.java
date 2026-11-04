package com.example.catalog;

import java.util.List;

/** Thin web layer over {@link BookService}. */
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    public Book get(String isbn) {
        return service.require(isbn);
    }

    public List<Book> list() {
        return service.recentBooks(1900);
    }

    public Book create(Book book) {
        return service.register(book);
    }
}
