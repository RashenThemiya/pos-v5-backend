package com.pos.system.controller;

import com.pos.system.dto.branch.BranchRequest;
import com.pos.system.model.auth.Branch;
import com.pos.system.service.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
@CrossOrigin
public class BranchController {

    private final BranchService branchService;

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRANCH_CREATE')")
    @PostMapping
    public ResponseEntity<Branch> create(@RequestBody BranchRequest request) {
        return ResponseEntity.ok(branchService.create(request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRANCH_VIEW')")
    @GetMapping
    public ResponseEntity<List<Branch>> getAll() {
        return ResponseEntity.ok(branchService.getAll());
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRANCH_VIEW')")
    @GetMapping("/{id}")
    public ResponseEntity<Branch> getById(@PathVariable Long id) {
        return ResponseEntity.ok(branchService.getById(id));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRANCH_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<Branch> update(@PathVariable Long id, @RequestBody BranchRequest request) {
        return ResponseEntity.ok(branchService.update(id, request));
    }

    @PreAuthorize("hasAuthority('ALL_PRIVILEGES') or hasAuthority('BRANCH_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        branchService.delete(id);
        return ResponseEntity.ok("Branch deleted successfully");
    }
}