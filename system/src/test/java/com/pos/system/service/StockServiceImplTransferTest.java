package com.pos.system.service;

import com.pos.system.dto.stock.StockTransferItemRequestDto;
import com.pos.system.dto.stock.StockTransferRequestDto;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.stock.Stock;
import com.pos.system.model.stock.StockBatch;
import com.pos.system.model.stock.StockMovement;
import com.pos.system.model.stock.StockTransfer;
import com.pos.system.model.stock.StockTransferItem;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceImplTransferTest {

    private static final Long FROM_BRANCH_ID = 1L;
    private static final Long TO_BRANCH_ID = 2L;
    private static final Long SOURCE_ITEM_ID = 10L;
    private static final Long DESTINATION_ITEM_ID = 20L;
    private static final Long SOURCE_UNIT_ID = 100L;
    private static final Long DESTINATION_UNIT_ID = 200L;
    private static final Long USER_ID = 7L;
    private static final Long TRANSFER_ID = 900L;
    private static final String SKU = "MILK-1L";
    private static final String BATCH_BARCODE = "BT-1-10-SRC";
    private static final String UNIT_BARCODE = "4790000000012";

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
    }

    @Test
    void transferUsesDestinationBranchCatalogIdsForReceivedStock() {
        Item sourceItem = item(SOURCE_ITEM_ID, FROM_BRANCH_ID);
        Item destinationItem = item(DESTINATION_ITEM_ID, TO_BRANCH_ID);
        ItemUnit sourceUnit = unit(SOURCE_UNIT_ID, SOURCE_ITEM_ID, FROM_BRANCH_ID);
        ItemUnit destinationUnit = unit(DESTINATION_UNIT_ID, DESTINATION_ITEM_ID, TO_BRANCH_ID);
        Stock sourceStock = stock(FROM_BRANCH_ID, SOURCE_ITEM_ID, SOURCE_UNIT_ID);
        StockBatch sourceBatch = sourceBatch();

        AtomicReference<StockTransfer> savedTransfer = new AtomicReference<>();
        List<StockTransferItem> savedTransferItems = new ArrayList<>();

        when(stockBatchRepository.findById(501L)).thenReturn(Optional.of(sourceBatch));
        when(stockBatchRepository.findByBranchIdAndInternalBatchBarcode(eq(TO_BRANCH_ID), anyString()))
                .thenReturn(Optional.empty());
        when(itemRepository.findByItemIdAndIsActiveTrue(SOURCE_ITEM_ID)).thenReturn(Optional.of(sourceItem));
        when(itemRepository.findByItemIdAndBranchId(SOURCE_ITEM_ID, FROM_BRANCH_ID)).thenReturn(Optional.of(sourceItem));
        when(itemRepository.findByBranchIdAndSku(TO_BRANCH_ID, SKU)).thenReturn(Optional.of(destinationItem));
        when(itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(SOURCE_ITEM_ID, SOURCE_UNIT_ID))
                .thenReturn(Optional.of(sourceUnit));
        when(itemUnitRepository.findByItemIdAndIsBaseUnitTrue(DESTINATION_ITEM_ID)).thenReturn(Optional.of(destinationUnit));
        when(itemUnitRepository.findByItemIdAndIsActiveTrue(DESTINATION_ITEM_ID)).thenReturn(List.of(destinationUnit));
        when(itemUnitRepository.findByBranchIdAndBarcodeAndIsActiveTrue(TO_BRANCH_ID, UNIT_BARCODE))
                .thenReturn(Optional.of(destinationUnit));
        when(itemUnitRepository.findById(SOURCE_UNIT_ID)).thenReturn(Optional.of(sourceUnit));
        when(unitConversionService.toBaseQty(anyLong(), anyLong(), any(BigDecimal.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(stockRepository.findByBranchIdAndItemIdAndVariantId(FROM_BRANCH_ID, SOURCE_ITEM_ID, null))
                .thenReturn(Optional.of(sourceStock));
        when(stockRepository.findByBranchIdAndItemIdAndVariantId(TO_BRANCH_ID, DESTINATION_ITEM_ID, null))
                .thenReturn(Optional.empty());
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockBatchRepository.save(any(StockBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(invocation -> {
            StockTransfer transfer = invocation.getArgument(0);
            transfer.setTransferId(TRANSFER_ID);
            savedTransfer.set(transfer);
            return transfer;
        });
        when(stockTransferRepository.findById(TRANSFER_ID)).thenAnswer(invocation -> Optional.of(savedTransfer.get()));
        when(stockTransferItemRepository.save(any(StockTransferItem.class))).thenAnswer(invocation -> {
            StockTransferItem item = invocation.getArgument(0);
            item.setTransferItemId(333L);
            savedTransferItems.add(item);
            return item;
        });
        when(stockTransferItemRepository.findByTransferId(TRANSFER_ID)).thenAnswer(invocation -> savedTransferItems);

        service.createStockTransfer(transferRequest());

        assertThat(sourceBatch.getAvailableQty()).isEqualByComparingTo("7");
        assertThat(sourceBatch.getQtyRemaining()).isEqualByComparingTo("7");

        ArgumentCaptor<StockBatch> batchCaptor = ArgumentCaptor.forClass(StockBatch.class);
        verify(stockBatchRepository, org.mockito.Mockito.times(2)).save(batchCaptor.capture());
        StockBatch destinationBatch = batchCaptor.getAllValues().stream()
                .filter(batch -> TO_BRANCH_ID.equals(batch.getBranchId()))
                .findFirst()
                .orElseThrow();
        assertThat(destinationBatch.getItemId()).isEqualTo(DESTINATION_ITEM_ID);
        assertThat(destinationBatch.getUnitId()).isEqualTo(DESTINATION_UNIT_ID);
        assertThat(destinationBatch.getReceivedQty()).isEqualByComparingTo("3");
        assertThat(destinationBatch.getReceivedBaseQty()).isEqualByComparingTo("3");
        assertThat(destinationBatch.getAvailableQty()).isEqualByComparingTo("3");
        assertThat(destinationBatch.getSupplyProductId()).isZero();

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, org.mockito.Mockito.times(2)).save(movementCaptor.capture());
        StockMovement transferOut = movementCaptor.getAllValues().stream()
                .filter(movement -> "TRANSFER_OUT".equals(movement.getMovementType()))
                .findFirst()
                .orElseThrow();
        StockMovement transferIn = movementCaptor.getAllValues().stream()
                .filter(movement -> "TRANSFER_IN".equals(movement.getMovementType()))
                .findFirst()
                .orElseThrow();

        assertThat(transferOut.getBranchId()).isEqualTo(FROM_BRANCH_ID);
        assertThat(transferOut.getItemId()).isEqualTo(SOURCE_ITEM_ID);
        assertThat(transferOut.getUnitId()).isEqualTo(SOURCE_UNIT_ID);
        assertThat(transferIn.getBranchId()).isEqualTo(TO_BRANCH_ID);
        assertThat(transferIn.getItemId()).isEqualTo(DESTINATION_ITEM_ID);
        assertThat(transferIn.getUnitId()).isEqualTo(DESTINATION_UNIT_ID);
    }

    @Test
    void transferCreatesDestinationCatalogWhenSkuDoesNotExistInTargetBranch() {
        Item sourceItem = item(SOURCE_ITEM_ID, FROM_BRANCH_ID);
        ItemUnit sourceUnit = unit(SOURCE_UNIT_ID, SOURCE_ITEM_ID, FROM_BRANCH_ID);
        Stock sourceStock = stock(FROM_BRANCH_ID, SOURCE_ITEM_ID, SOURCE_UNIT_ID);
        StockBatch sourceBatch = sourceBatch();

        AtomicReference<Item> savedDestinationItem = new AtomicReference<>();
        AtomicReference<ItemUnit> savedDestinationUnit = new AtomicReference<>();
        AtomicReference<StockTransfer> savedTransfer = new AtomicReference<>();
        List<StockTransferItem> savedTransferItems = new ArrayList<>();

        when(stockBatchRepository.findById(501L)).thenReturn(Optional.of(sourceBatch));
        when(stockBatchRepository.findByBranchIdAndInternalBatchBarcode(eq(TO_BRANCH_ID), anyString()))
                .thenReturn(Optional.empty());
        when(itemRepository.findByItemIdAndIsActiveTrue(SOURCE_ITEM_ID)).thenReturn(Optional.of(sourceItem));
        when(itemRepository.findByItemIdAndBranchId(SOURCE_ITEM_ID, FROM_BRANCH_ID)).thenReturn(Optional.of(sourceItem));
        when(itemRepository.findByBranchIdAndSku(TO_BRANCH_ID, SKU))
                .thenAnswer(invocation -> Optional.ofNullable(savedDestinationItem.get()));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            item.setItemId(DESTINATION_ITEM_ID);
            savedDestinationItem.set(item);
            return item;
        });
        when(itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(SOURCE_ITEM_ID, SOURCE_UNIT_ID))
                .thenReturn(Optional.of(sourceUnit));
        when(itemUnitRepository.findByItemId(SOURCE_ITEM_ID)).thenReturn(List.of(sourceUnit));
        when(itemUnitRepository.findByItemIdAndIsBaseUnitTrue(DESTINATION_ITEM_ID))
                .thenAnswer(invocation -> Optional.ofNullable(savedDestinationUnit.get()));
        when(itemUnitRepository.findByItemIdAndIsActiveTrue(DESTINATION_ITEM_ID))
                .thenAnswer(invocation -> savedDestinationUnit.get() == null ? List.of() : List.of(savedDestinationUnit.get()));
        when(itemUnitRepository.findByBranchIdAndBarcodeAndIsActiveTrue(TO_BRANCH_ID, UNIT_BARCODE))
                .thenAnswer(invocation -> Optional.ofNullable(savedDestinationUnit.get()));
        when(itemUnitRepository.findById(SOURCE_UNIT_ID)).thenReturn(Optional.of(sourceUnit));
        when(itemUnitRepository.save(any(ItemUnit.class))).thenAnswer(invocation -> {
            ItemUnit unit = invocation.getArgument(0);
            unit.setUnitId(DESTINATION_UNIT_ID);
            savedDestinationUnit.set(unit);
            return unit;
        });
        when(itemVariantRepository.findByItemIdOrderByVariantIdAsc(SOURCE_ITEM_ID)).thenReturn(List.of());
        when(unitConversionService.toBaseQty(anyLong(), anyLong(), any(BigDecimal.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(stockRepository.findByBranchIdAndItemIdAndVariantId(FROM_BRANCH_ID, SOURCE_ITEM_ID, null))
                .thenReturn(Optional.of(sourceStock));
        when(stockRepository.findByBranchIdAndItemIdAndVariantId(TO_BRANCH_ID, DESTINATION_ITEM_ID, null))
                .thenReturn(Optional.empty());
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockBatchRepository.save(any(StockBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(invocation -> {
            StockTransfer transfer = invocation.getArgument(0);
            transfer.setTransferId(TRANSFER_ID);
            savedTransfer.set(transfer);
            return transfer;
        });
        when(stockTransferRepository.findById(TRANSFER_ID)).thenAnswer(invocation -> Optional.of(savedTransfer.get()));
        when(stockTransferItemRepository.save(any(StockTransferItem.class))).thenAnswer(invocation -> {
            StockTransferItem item = invocation.getArgument(0);
            item.setTransferItemId(334L);
            savedTransferItems.add(item);
            return item;
        });
        when(stockTransferItemRepository.findByTransferId(TRANSFER_ID)).thenAnswer(invocation -> savedTransferItems);

        service.createStockTransfer(transferRequest());

        assertThat(savedDestinationItem.get()).isNotNull();
        assertThat(savedDestinationItem.get().getBranchId()).isEqualTo(TO_BRANCH_ID);
        assertThat(savedDestinationItem.get().getSku()).isEqualTo(SKU);
        assertThat(savedDestinationItem.get().getIsActive()).isTrue();
        assertThat(savedDestinationUnit.get()).isNotNull();
        assertThat(savedDestinationUnit.get().getBranchId()).isEqualTo(TO_BRANCH_ID);
        assertThat(savedDestinationUnit.get().getItemId()).isEqualTo(DESTINATION_ITEM_ID);
        assertThat(savedDestinationUnit.get().getUnitName()).isEqualTo("PCS");

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, org.mockito.Mockito.times(2)).save(movementCaptor.capture());
        StockMovement transferIn = movementCaptor.getAllValues().stream()
                .filter(movement -> "TRANSFER_IN".equals(movement.getMovementType()))
                .findFirst()
                .orElseThrow();
        assertThat(transferIn.getItemId()).isEqualTo(DESTINATION_ITEM_ID);
        assertThat(transferIn.getUnitId()).isEqualTo(DESTINATION_UNIT_ID);
    }

    private StockTransferRequestDto transferRequest() {
        StockTransferItemRequestDto item = new StockTransferItemRequestDto();
        item.setItemId(SOURCE_ITEM_ID);
        item.setUnitId(SOURCE_UNIT_ID);
        item.setStockBatchId(501L);
        item.setQuantity(new BigDecimal("3"));

        StockTransferRequestDto request = new StockTransferRequestDto();
        request.setFromBranchId(FROM_BRANCH_ID);
        request.setToBranchId(TO_BRANCH_ID);
        request.setCreatedBy(USER_ID);
        request.setNote("Restock branch");
        request.setItems(List.of(item));
        return request;
    }

    private Item item(Long itemId, Long branchId) {
        Item item = new Item();
        item.setItemId(itemId);
        item.setBranchId(branchId);
        item.setName("Fresh Milk 1L");
        item.setSku(SKU);
        item.setIsActive(true);
        return item;
    }

    private ItemUnit unit(Long unitId, Long itemId, Long branchId) {
        ItemUnit unit = new ItemUnit();
        unit.setUnitId(unitId);
        unit.setBranchId(branchId);
        unit.setItemId(itemId);
        unit.setUnitName("PCS");
        unit.setMultiplierToBase(BigDecimal.ONE);
        unit.setBarcode(UNIT_BARCODE);
        unit.setIsBaseUnit(true);
        unit.setIsActive(true);
        return unit;
    }

    private Stock stock(Long branchId, Long itemId, Long unitId) {
        Stock stock = new Stock();
        stock.setStockId(70L);
        stock.setBranchId(branchId);
        stock.setItemId(itemId);
        stock.setUnitId(unitId);
        return stock;
    }

    private StockBatch sourceBatch() {
        StockBatch batch = new StockBatch();
        batch.setStockBatchId(501L);
        batch.setBranchId(FROM_BRANCH_ID);
        batch.setItemId(SOURCE_ITEM_ID);
        batch.setSupplyProductId(444L);
        batch.setUnitId(SOURCE_UNIT_ID);
        batch.setReceivedQty(new BigDecimal("10"));
        batch.setReceivedBaseQty(new BigDecimal("10"));
        batch.setQtyRemaining(new BigDecimal("10"));
        batch.setAvailableQty(new BigDecimal("10"));
        batch.setDamagedQty(BigDecimal.ZERO);
        batch.setExpiredQty(BigDecimal.ZERO);
        batch.setInternalBatchBarcode(BATCH_BARCODE);
        batch.setBatchNo("MILK-B1");
        batch.setCostPrice(new BigDecimal("250"));
        batch.setSellingPrice(new BigDecimal("320"));
        batch.setCreatedAt(LocalDateTime.now());
        return batch;
    }
}
