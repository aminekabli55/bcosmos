package com.bcosmos.backend.web;

import com.bcosmos.backend.ai.gateway.ProviderRegistry;
import com.bcosmos.backend.web.dto.CreateProviderRequest;
import com.bcosmos.backend.web.dto.PagedResponse;
import com.bcosmos.backend.web.dto.ProviderResponse;
import com.bcosmos.backend.web.dto.UpdateProviderRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The provider registry as the settings screen uses it (spec v2 section 8).
 *
 * <p>This is the endpoint that makes "support another vendor" a data change: a provider is registered with the
 * protocol it speaks, its base URL and optionally a key, and its models are added through {@code /api/models}
 * (ADR 0038). No request or response in this controller ever carries a stored credential back out — only a source
 * and a four-character hint.</p>
 *
 * <p>Paginated like every other list endpoint.</p>
 */
@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderRegistry providers;

    public ProviderController(ProviderRegistry providers) {
        this.providers = providers;
    }

    /** {@code GET /api/providers} — the registry, paginated. */
    @GetMapping
    public PagedResponse<ProviderResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<ProviderResponse> all = providers.listing().stream().map(ProviderResponse::of).toList();
        int pageSize = Pagination.clamp(size);
        int pageIndex = Math.max(page, 0);
        int from = Math.min(pageIndex * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        int totalPages = (int) Math.ceil(all.size() / (double) pageSize);
        return new PagedResponse<>(all.subList(from, to), pageIndex, pageSize, all.size(), totalPages, to < all.size());
    }

    /** {@code POST /api/providers} — registers a vendor, its endpoint and optionally its credential. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderResponse create(@Valid @RequestBody CreateProviderRequest request) {
        return ProviderResponse.of(providers.create(request.toDraft()));
    }

    /** {@code PUT /api/providers/{id}} — edits the endpoint, or stores, replaces or clears the credential. */
    @PutMapping("/{id}")
    public ProviderResponse update(@PathVariable long id, @Valid @RequestBody UpdateProviderRequest request) {
        return ProviderResponse.of(providers.edit(id, request.toEdit()));
    }

    /**
     * {@code DELETE /api/providers/{id}} — removes a provider no registry row references.
     *
     * <p>Runs, usage rows and commit reviews keep their own copy of the provider code, so history stays readable
     * after a provider is gone.</p>
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        providers.delete(id);
    }
}
