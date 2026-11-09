package com.pos.system.config;

import com.pos.system.model.auth.Permission;
import com.pos.system.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class InventoryImportPermissions implements ApplicationRunner {
    private final PermissionRepository permissions;

    @Override @Transactional
    public void run(ApplicationArguments args) {
        Map.of(
            "INVENTORY_BULK_TEMPLATE_DOWNLOAD","Download inventory templates",
            "INVENTORY_BULK_VALIDATE","Validate inventory workbooks",
            "INVENTORY_BULK_CATALOG_IMPORT","Post catalog imports",
            "INVENTORY_BULK_OPENING_STOCK","Post opening stock imports",
            "INVENTORY_BULK_HISTORY_VIEW","View inventory import history",
            "STOCK_COUNT_EXPORT","Export physical stock counts",
            "STOCK_COUNT_UPLOAD","Upload physical stock counts",
            "STOCK_COUNT_APPROVE","Approve and post physical stock counts"
        ).forEach((code,description)->{
            if(!permissions.existsByCode(code)) permissions.save(new Permission(null,code,description,"INVENTORY_IMPORT",LocalDateTime.now()));
        });
    }
}
