package com.pos.system.service;

import com.pos.system.dto.stock.StockAdjustRequest;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.stock.Stock;
import com.pos.system.model.stock.StockBatch;
import com.pos.system.model.stock.StockMovement;
import com.pos.system.repository.BrandRepository;
import com.pos.system.repository.CategoryRepository;
import com.pos.system.repository.ItemRepository;
import com.pos.system.repository.ItemUnitRepository;
import com.pos.system.repository.ItemVariantAttributeRepository;
import com.pos.system.repository.ItemVariantRepository;
import com.pos.system.repository.StockBatchRepository;
import com.pos.system.repository.StockCountItemRepository;
import com.pos.system.repository.StockCountRepository;
import com.pos.system.repository.StockMovementRepository;
import com.pos.system.repository.StockRepository;
import com.pos.system.repository.StockTransferItemRepository;
import com.pos.system.repository.StockTransferRepository;
import com.pos.system.repository.UnitMasterRepository;
import com.pos.system.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceImplDamageAdjustmentTest {

    private static final Long BRANCH_ID = 2L;
    private static final Long ITEM_ID = 10L;
    private static final Long UNIT_ID = 20L;
    private static final Long USER_ID = 7L;
    private static final String BATCH_BARCODE = "BT-2-10-TEST";

    @Mock private StockRepository stockRepository;
    @Mock private StockBatchRepository stockBatchRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private StockTransferRepository stockTransferRepository;
    @Mock private StockTransferItemRepository stockTransferItemRepository;
    @Mock private StockCountRepository stockCountRepository;
    @Mock private StockCountItemRepository stockCountItemRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private ItemUnitRepository itemUnitRepository;
    @Mock private ItemVariantRepository itemVariantRepository;
    @Mock private ItemVariantAttributeRepository itemVariantAttributeRepository;
    @Mock private UnitMasterRepository unitMasterRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private UserRepository userRepository;
    @Mock private UnitConversionService unitConversionService;

    private StockServiceImpl service;
    private StockBatch batch;
    private Stock stock;

    @BeforeEach
    void setUp() {
        service = new StockServiceImpl(
                stockRepository,
                stockBatchRepository,
                stockMovementRepository,
                stockTransferRepository,
                stockTransferItemRepository,
                stockCountRepository,
                stockCountItemRepository,
                itemRepository,
                itemUnitRepository,
                itemVariantRepository,
                itemVariantAttributeRepository,
                unitMasterRepository,
                categoryRepository,
                brandRepository,
                userRepository,
                unitConversionService
        );

        Item item = new Item();
        item.setItemId(ITEM_ID);
        item.setBranchId(BRANCH_ID);
        item.setName("Coca-Cola 500ml");
        item.setSku("CC-500");
        item.setIsActive(true);

        ItemUnit unit = new ItemUnit();
        unit.setUnitId(UNIT_ID);
        unit.setBranchId(BRANCH_ID);
        unit.setItemId(ITEM_ID);
        unit.setUnitName("PACK");
        unit.setMultiplierToBase(BigDecimal.ONE);
        unit.setIsBaseUnit(true);
        unit.setIsActive(true);

        stock = new Stock();
        stock.setStockId(30L);
        stock.setBranchId(BRANCH_ID);
        stock.setItemId(ITEM_ID);
        stock.setUnitId(UNIT_ID);

        batch = new StockBatch();
        batch.setStockBatchId(40L);
        batch.setBranchId(BRANCH_ID);
        batch.setItemId(ITEM_ID);
        batch.setSupplyProductId(50L);
        batch.setUnitId(UNIT_ID);
        batch.setReceivedQty(new BigDecimal("10"));
        batch.setReceivedBaseQty(new BigDecimal("10"));
        batch.setQtyRemaining(new BigDecimal("10"));
        batch.setAvailableQty(new BigDecimal("10"));
        batch.setDamagedQty(BigDecimal.ZERO);
        batch.setExpiredQty(BigDecimal.ZERO);
        batch.setInternalBatchBarcode(BATCH_BARCODE);
        batch.setBatchNo("BATCH-CC-0826-02");
        batch.setCreatedAt(LocalDateTime.now());

        when(itemRepository.findByItemIdAndIsActiveTrue(ITEM_ID)).thenReturn(Optional.of(item));
        when(itemRepository.findByItemIdAndBranchId(ITEM_ID, BRANCH_ID)).thenReturn(Optional.of(item));
        when(itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(ITEM_ID, UNIT_ID)).thenReturn(Optional.of(unit));
        when(itemUnitRepository.findById(UNIT_ID)).thenReturn(Optional.of(unit));
        when(unitConversionService.toBaseQty(anyLong(), anyLong(), any(BigDecimal.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(stockRepository.findByBranchIdAndItemIdAndVariantId(BRANCH_ID, ITEM_ID, null))
                .thenReturn(Optional.of(stock));
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockBatchRepository.findByBranchIdAndInternalBatchBarcode(BRANCH_ID, BATCH_BARCODE))
                .thenReturn(Optional.of(batch));
        when(stockBatchRepository.save(any(StockBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void availableStockCanBeMarkedAsDamagedForSelectedBatch() {
        service.adjustStock(damageRequest("2"));

        assertThat(batch.getAvailableQty()).isEqualByComparingTo("8");
        assertThat(batch.getQtyRemaining()).isEqualByComparingTo("8");
        assertThat(batch.getDamagedQty()).isEqualByComparingTo("2");

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movementCaptor.capture());

        StockMovement movement = movementCaptor.getValue();
        assertThat(movement.getMovementType()).isEqualTo("AVAILABLE_TO_DAMAGED");
        assertThat(movement.getBranchId()).isEqualTo(BRANCH_ID);
        assertThat(movement.getItemId()).isEqualTo(ITEM_ID);
        assertThat(movement.getUnitId()).isEqualTo(UNIT_ID);
        assertThat(movement.getInternalBatchBarcode()).isEqualTo(BATCH_BARCODE);
        assertThat(movement.getQuantity()).isEqualByComparingTo("2");
        assertThat(movement.getRefTable()).isEqualTo("stock_batches");
        assertThat(movement.getRefId()).isEqualTo(40L);
        assertThat(movement.getNote()).isEqualTo("Broken packaging");
        assertThat(movement.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(movement.getCreatedAt()).isNotNull();
    }

    @Test
    void cannotMarkMoreThanAvailableStockAsDamaged() {
        assertThatThrownBy(() -> service.adjustStock(damageRequest("11")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not enough available stock in batch");
    }

    private StockAdjustRequest damageRequest(String quantity) {
        StockAdjustRequest request = new StockAdjustRequest();
        request.setBranchId(BRANCH_ID);
        request.setItemId(ITEM_ID);
        request.setUnitId(UNIT_ID);
        request.setInternalBatchBarcode(BATCH_BARCODE);
        request.setQuantity(new BigDecimal(quantity));
        request.setAdjustmentType("AVAILABLE_TO_DAMAGED");
        request.setNote("Broken packaging");
        request.setCreatedBy(USER_ID);
        return request;
    }
}
