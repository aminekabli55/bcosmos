package com.example.catalog;

/** Domain type of the fixture catalogue. */
public record Book(String isbn, String title, String author, int year) {

    public Book {
        if (isbn == null || isbn.isBlank()) {
            throw new IllegalArgumentException("isbn is required");
        }
    }

    public boolean publishedAfter(int otherYear) {
        return year > otherYear;
    }
}
