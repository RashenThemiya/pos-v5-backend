package com.pos.system.controller;

import com.pos.system.repository.*;
import com.pos.system.service.InventoryImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
@RequestMapping("/api/branches/{branchId}/inventory-import")
@RequiredArgsConstructor
public class InventoryImportController {
    private final InventoryImportService service;
    private final AuthorizationRepository authorizations;
    private final UserRepository users;
    private final BranchRepository branches;

    private Long authorize(Long branch,Authentication auth) {
        if(!branches.existsById(branch)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Branch not found");
        var authorization=authorizations.findByUsername(auth.getName()).orElseThrow();
        var user=users.findByAuthId(authorization.getAuthId());
        boolean admin=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ALL_PRIVILEGES"));
        if(!admin && (user.isEmpty() || !Objects.equals(user.get().getBranchId(),branch))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch access denied");
        return user.map(u->u.getUserId()).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"A staff user record is required to post inventory"));
    }
    private boolean has(Authentication auth,String code) {return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ALL_PRIVILEGES")||a.getAuthority().equals(code));}
    @GetMapping("/capabilities")
    public Object capabilities(@PathVariable Long branchId,Authentication auth) {
        authorize(branchId,auth);
        return Map.of("catalog",has(auth,"INVENTORY_BULK_CATALOG_IMPORT"),"opening",has(auth,"INVENTORY_BULK_OPENING_STOCK"),"count",has(auth,"STOCK_COUNT_UPLOAD")||has(auth,"STOCK_COUNT_CREATE"),"approve",has(auth,"STOCK_COUNT_APPROVE"),"validate",has(auth,"INVENTORY_BULK_VALIDATE"),"template",has(auth,"INVENTORY_BULK_TEMPLATE_DOWNLOAD"),"countExport",has(auth,"STOCK_COUNT_EXPORT")||has(auth,"STOCK_COUNT_VIEW"),"catalogExport",has(auth,"ITEM_VIEW")||has(auth,"INVENTORY_BULK_CATALOG_IMPORT"));
    }
    @GetMapping("/jobs")
    @PreAuthorize("hasAnyAuthority('ALL_PRIVILEGES','STOCK_VIEW','STOCK_COUNT_VIEW','INVENTORY_BULK_HISTORY_VIEW')")
    public Object history(@PathVariable Long branchId,Authentication auth) {authorize(branchId,auth);return service.history(branchId);}
    @GetMapping("/jobs/{id}/report")
    @PreAuthorize("hasAnyAuthority('ALL_PRIVILEGES','STOCK_VIEW','STOCK_COUNT_VIEW','INVENTORY_BULK_HISTORY_VIEW')")
    public Object report(@PathVariable Long branchId,@PathVariable Long id,Authentication auth) {authorize(branchId,auth);return service.report(branchId,id);}
    @GetMapping("/count-export")
    @PreAuthorize("hasAnyAuthority('ALL_PRIVILEGES','STOCK_COUNT_VIEW','STOCK_COUNT_EXPORT')")
    public Object export(@PathVariable Long branchId,Authentication auth) {authorize(branchId,auth);return service.exportCount(branchId);}
    @GetMapping("/catalog-export")
    @PreAuthorize("hasAnyAuthority('ALL_PRIVILEGES','ITEM_VIEW','INVENTORY_BULK_CATALOG_IMPORT')")
    public Object catalogExport(@PathVariable Long branchId,Authentication auth) {authorize(branchId,auth);return service.exportCatalog(branchId);}
    @PostMapping("/jobs")
    @PreAuthorize("hasAnyAuthority('ALL_PRIVILEGES','STOCK_COUNT_CREATE','STOCK_COUNT_UPLOAD','INVENTORY_BULK_VALIDATE')")
    public Object upload(@PathVariable Long branchId,@RequestBody InventoryImportService.Upload upload,Authentication auth) {
        authorize(branchId,auth);
        if(!"COUNT".equals(upload.mode()) && !has(auth,"INVENTORY_BULK_VALIDATE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Inventory validation permission required");
        return service.validate(branchId,upload,auth.getName());
    }
    @PostMapping("/jobs/{id}/confirm")
    @PreAuthorize("hasAnyAuthority('ALL_PRIVILEGES','STOCK_COUNT_APPROVE','INVENTORY_BULK_CATALOG_IMPORT','INVENTORY_BULK_OPENING_STOCK')")
    public Object confirm(@PathVariable Long branchId,@PathVariable Long id,Authentication auth) {
        Long user=authorize(branchId,auth);
        var job=service.get(branchId,id);
        String permission=switch(job.mode()){case "CATALOG"->"INVENTORY_BULK_CATALOG_IMPORT";case "OPENING"->"INVENTORY_BULK_OPENING_STOCK";default->"STOCK_COUNT_APPROVE";};
        if("INITIAL".equals(job.mode())) {if(!has(auth,"INVENTORY_BULK_CATALOG_IMPORT")||!has(auth,"INVENTORY_BULK_OPENING_STOCK"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Initial stock requires catalog and opening-stock permissions");permission="INVENTORY_BULK_OPENING_STOCK";}
        if(!has(auth,permission))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Permission required: "+permission);
        try {return service.confirm(branchId,id,auth.getName(),user);}
        catch(RuntimeException e) {service.recordFailure(branchId,id,e.getMessage());throw e;}
    }
    @PostMapping("/jobs/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ALL_PRIVILEGES','STOCK_COUNT_APPROVE','INVENTORY_BULK_CATALOG_IMPORT','INVENTORY_BULK_OPENING_STOCK')")
    public Object cancel(@PathVariable Long branchId,@PathVariable Long id,Authentication auth) {
        authorize(branchId,auth);var job=service.get(branchId,id);
        String permission=switch(job.mode()){case "CATALOG"->"INVENTORY_BULK_CATALOG_IMPORT";case "OPENING"->"INVENTORY_BULK_OPENING_STOCK";default->"STOCK_COUNT_APPROVE";};
        if("INITIAL".equals(job.mode())) {if(!has(auth,"INVENTORY_BULK_CATALOG_IMPORT")||!has(auth,"INVENTORY_BULK_OPENING_STOCK"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);permission="INVENTORY_BULK_OPENING_STOCK";}
        if(!has(auth,permission))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return service.cancel(branchId,id);
    }
}
