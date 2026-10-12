package com.bcosmos.backend.web.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Page envelope for every list endpoint (spec v2 section 8: "pagination everywhere").
 *
 * @param items      the page content
 * @param page       zero-based page index
 * @param size       requested page size
 * @param totalItems total matching rows
 * @param totalPages total pages
 * @param hasNext    whether another page exists
 */
public record PagedResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages, boolean hasNext) {

    public PagedResponse {
        items = List.copyOf(items);
    }

    /** Maps a Spring Data page of entities to a page of DTOs. Entities never leave the service layer. */
    public static <S, T> PagedResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
