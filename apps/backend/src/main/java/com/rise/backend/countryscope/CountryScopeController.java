package com.rise.backend.countryscope;

import com.rise.backend.auth.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/country-scopes")
public class CountryScopeController {

    private final CountryScopeService countryScopeService;

    public CountryScopeController(CountryScopeService countryScopeService) {
        this.countryScopeService = countryScopeService;
    }

    @GetMapping
    public ResponseEntity<List<CountryScopeResponse>> findAll(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(countryScopeService.findAll(includeInactive));
    }

    @PostMapping
    public ResponseEntity<CountryScopeResponse> create(@Valid @RequestBody CreateCountryScopeRequest request, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        CountryScopeResponse created = countryScopeService.create(request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CountryScopeResponse> update(@PathVariable Integer id, @Valid @RequestBody UpdateCountryScopeRequest request, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(countryScopeService.update(id, request, principal.userId()));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Integer id, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        countryScopeService.logicalDelete(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
