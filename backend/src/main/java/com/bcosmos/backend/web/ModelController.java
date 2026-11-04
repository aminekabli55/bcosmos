package com.bcosmos.backend.web;

import com.bcosmos.backend.ai.gateway.ModelRegistry;
import com.bcosmos.backend.web.dto.CreateModelRequest;
import com.bcosmos.backend.web.dto.ModelResponse;
import com.bcosmos.backend.web.dto.PagedResponse;
import com.bcosmos.backend.web.dto.UpdateModelRequest;
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
 * The model registry as the settings screen uses it (spec v2 section 8: {@code GET /api/models},
 * {@code POST /api/models}, {@code PUT /api/models/{id}}, {@code DELETE /api/models/{id}}).
 *
 * <p>The list is paginated like every other list endpoint, over the registry rows in memory: there are a handful of
 * models, and the settings screen asks for one page of them.</p>
 *
 * <p>Since Phase 11 a model can be registered and removed at runtime, which is what makes "use every model" real
 * rather than a seeded list: the provider is chosen by id, its endpoint and credential belong to the provider row,
 * and this table only holds the name, the capability and the prices (ADR 0038).</p>
 */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ModelRegistry models;

    public ModelController(ModelRegistry models) {
        this.models = models;
    }

    /** {@code GET /api/models} — the registry, paginated. */
    @GetMapping
    public PagedResponse<ModelResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<ModelResponse> all = models.listing().stream().map(ModelResponse::of).toList();
        int pageSize = Pagination.clamp(size);
        int pageIndex = Math.max(page, 0);
        int from = Math.min(pageIndex * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        int totalPages = (int) Math.ceil(all.size() / (double) pageSize);
        return new PagedResponse<>(all.subList(from, to), pageIndex, pageSize, all.size(), totalPages, to < all.size());
    }

    /** {@code POST /api/models} — registers a model of an existing provider. It starts disabled unless asked. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModelResponse create(@Valid @RequestBody CreateModelRequest request) {
        return ModelResponse.of(models.create(request.toDraft()));
    }

    /** {@code PUT /api/models/{id}} — enable, disable, make default, or correct prices and notes. */
    @PutMapping("/{id}")
    public ModelResponse update(@PathVariable long id, @Valid @RequestBody UpdateModelRequest request) {
        return ModelResponse.of(models.edit(id, request.toEdit()));
    }

    /**
     * {@code DELETE /api/models/{id}} — removes a registry row.
     *
     * <p>The default row cannot be removed first: the API answers a request that names no model with the default.
     * Usage rows and suggestions keep their own copy of the name, so history survives the deletion.</p>
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        models.delete(id);
    }
}
