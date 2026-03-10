package com.pos.system.service;

import com.pos.system.dto.branch.BranchRequest;
import com.pos.system.model.auth.Branch;
import com.pos.system.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    public Branch create(BranchRequest request) {
        if (branchRepository.existsByName(request.getName())) {
            throw new RuntimeException("Branch name already exists");
        }

        Branch branch = new Branch();
        branch.setName(request.getName());
        branch.setAddress(request.getAddress());
        branch.setPhone(request.getPhone());
        branch.setTimezone(request.getTimezone());
        branch.setIsActive(request.getIsActive() == null ? true : request.getIsActive());

        return branchRepository.save(branch);
    }

    public List<Branch> getAll() {
        return branchRepository.findAll();
    }

    public Branch getById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found"));
    }

    public Branch update(Long id, BranchRequest request) {
        Branch branch = getById(id);
        branch.setName(request.getName());
        branch.setAddress(request.getAddress());
        branch.setPhone(request.getPhone());
        branch.setTimezone(request.getTimezone());
        if (request.getIsActive() != null) {
            branch.setIsActive(request.getIsActive());
        }
        return branchRepository.save(branch);
    }

    public void delete(Long id) {
        Branch branch = getById(id);
        branchRepository.delete(branch);
    }
}