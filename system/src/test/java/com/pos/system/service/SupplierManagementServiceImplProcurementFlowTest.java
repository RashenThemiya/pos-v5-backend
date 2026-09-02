package com.pos.system.service;

import com.pos.system.dto.supplier.PurchaseOrderItemRequestDto;
import com.pos.system.dto.supplier.PurchaseOrderItemResponseDto;
import com.pos.system.dto.supplier.PurchaseOrderRequestDto;
import com.pos.system.dto.supplier.PurchaseOrderResponseDto;
import com.pos.system.dto.supplier.SupplierPaymentRequestDto;
import com.pos.system.dto.supplier.SupplyResponseDto;
import com.pos.system.dto.supplier.SupplyProductRequestDto;
import com.pos.system.dto.supplier.SupplyRequestDto;
import com.pos.system.model.cash.CashSession;
import com.pos.system.model.cash.CashSessionTransaction;
import com.pos.system.model.catalog.Item;
import com.pos.system.model.catalog.ItemUnit;
import com.pos.system.model.catalog.ItemVariant;
import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.Payment;
import com.pos.system.model.supplier.PurchaseOrder;
import com.pos.system.model.supplier.PurchaseOrderItem;
import com.pos.system.model.supplier.Supplier;
import com.pos.system.model.supplier.SupplierBalanceTransaction;
import com.pos.system.model.supplier.SupplierItem;
import com.pos.system.model.supplier.SupplierPayment;
import com.pos.system.model.supplier.Supply;
import com.pos.system.model.supplier.SupplyProduct;
import com.pos.system.repository.CashSessionRepository;
import com.pos.system.repository.CashSessionTransactionRepository;
import com.pos.system.repository.CustomerOrderRepository;
import com.pos.system.repository.ItemRepository;
import com.pos.system.repository.ItemUnitRepository;
import com.pos.system.repository.ItemVariantAttributeRepository;
import com.pos.system.repository.ItemVariantRepository;
import com.pos.system.repository.PaymentRepository;
import com.pos.system.repository.PurchaseOrderItemRepository;
import com.pos.system.repository.PurchaseOrderRepository;
import com.pos.system.repository.PurchaseReturnItemRepository;
import com.pos.system.repository.PurchaseReturnRepository;
import com.pos.system.repository.StockBatchRepository;
import com.pos.system.repository.StockMovementRepository;
import com.pos.system.repository.StockRepository;
import com.pos.system.repository.SupplierBalanceTransactionRepository;
import com.pos.system.repository.SupplierItemRepository;
import com.pos.system.repository.SupplierPaymentRepository;
import com.pos.system.repository.SupplierRepository;
import com.pos.system.repository.SupplyProductRepository;
import com.pos.system.repository.SupplyRepository;
import com.pos.system.repository.UnitMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierManagementServiceImplProcurementFlowTest {

    private static final Long BRANCH_ID = 2L;
    private static final Long SUPPLIER_ID = 1L;
    private static final Long USER_ID = 7L;
    private static final Long ITEM_A_ID = 10L;
    private static final Long UNIT_A_ID = 20L;
    private static final Long VARIANT_A_ID = 101L;
    private static final Long VARIANT_B_ID = 102L;
    private static final Long ITEM_B_ID = 11L;
    private static final Long UNIT_B_ID = 21L;

    @Mock private SupplierRepository supplierRepository;
    @Mock private SupplierItemRepository supplierItemRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private SupplyRepository supplyRepository;
    @Mock private SupplyProductRepository supplyProductRepository;
    @Mock private SupplierPaymentRepository supplierPaymentRepository;
    @Mock private SupplierBalanceTransactionRepository supplierBalanceTransactionRepository;
    @Mock private StockRepository stockRepository;
    @Mock private StockBatchRepository stockBatchRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private ItemUnitRepository itemUnitRepository;
    @Mock private ItemVariantRepository itemVariantRepository;
    @Mock private ItemVariantAttributeRepository itemVariantAttributeRepository;
    @Mock private UnitMasterRepository unitMasterRepository;
    @Mock private UnitConversionService unitConversionService;
    @Mock private CashSessionRepository cashSessionRepository;
    @Mock private CashSessionTransactionRepository cashSessionTransactionRepository;
    @Mock private CustomerOrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PurchaseReturnRepository purchaseReturnRepository;
    @Mock private PurchaseReturnItemRepository purchaseReturnItemRepository;

    private SupplierManagementServiceImpl service;
    private Supplier supplier;
    private final List<PurchaseOrder> purchaseOrders = new ArrayList<>();
    private final List<PurchaseOrderItem> purchaseOrderItems = new ArrayList<>();
    private final List<Supply> supplies = new ArrayList<>();
    private final List<SupplyProduct> supplyProducts = new ArrayList<>();
    private final List<SupplierPayment> supplierPayments = new ArrayList<>();
    private final List<SupplierBalanceTransaction> supplierLedger = new ArrayList<>();
    private long poSequence;
    private long supplySequence;

    @BeforeEach
    void setUp() {
        service = new SupplierManagementServiceImpl(
                supplierRepository,
                supplierItemRepository,
                purchaseOrderRepository,
                purchaseOrderItemRepository,
                supplyRepository,
                supplyProductRepository,
                supplierPaymentRepository,
                supplierBalanceTransactionRepository,
                stockRepository,
                stockBatchRepository,
                stockMovementRepository,
                itemRepository,
                itemUnitRepository,
                itemVariantRepository,
                itemVariantAttributeRepository,
                unitMasterRepository,
                unitConversionService,
                cashSessionRepository,
                cashSessionTransactionRepository,
                orderRepository,
                paymentRepository,
                purchaseReturnRepository,
                purchaseReturnItemRepository
        );

        poSequence = 90L;
        supplySequence = 50L;
        supplier = new Supplier();
        supplier.setSupplierId(SUPPLIER_ID);
        supplier.setBranchId(BRANCH_ID);
        supplier.setName("ABC Suppliers");
        supplier.setBalance(BigDecimal.ZERO);

        stubCatalogItem(ITEM_A_ID, UNIT_A_ID, "Coca-Cola");
        stubCatalogVariant(ITEM_A_ID, VARIANT_A_ID, "SKU-10-RED");
        stubCatalogVariant(ITEM_A_ID, VARIANT_B_ID, "SKU-10-BLUE");
        stubCatalogItem(ITEM_B_ID, UNIT_B_ID, "Sprite");

        when(supplierRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(supplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(supplierItemRepository.findByBranchIdAndSupplierIdAndItemIdAndUnitId(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenAnswer(invocation -> Optional.of(activeSupplierItem(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3)
                )));
        when(supplierItemRepository.findByBranchIdAndSupplierIdAndItemIdAndVariantIdAndUnitId(anyLong(), anyLong(), anyLong(), any(), anyLong()))
                .thenAnswer(invocation -> Optional.of(activeSupplierItem(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4)
                )));
        when(supplierItemRepository.save(any(SupplierItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(unitConversionService.toBaseQty(anyLong(), anyLong(), any(BigDecimal.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(stockRepository.findByBranchIdAndItemId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(stockRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockBatchRepository.findByBranchIdAndInternalBatchBarcode(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(stockBatchRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockMovementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(purchaseOrderRepository.findByPoNo(anyString())).thenReturn(Optional.empty());
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder purchaseOrder = invocation.getArgument(0);
            if (purchaseOrder.getPoId() == null) {
                purchaseOrder.setPoId(++poSequence);
                purchaseOrders.add(purchaseOrder);
            }
            return purchaseOrder;
        });
        when(purchaseOrderRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long poId = invocation.getArgument(0);
            return purchaseOrders.stream().filter(po -> po.getPoId().equals(poId)).findFirst();
        });
        when(purchaseOrderItemRepository.save(any(PurchaseOrderItem.class))).thenAnswer(invocation -> {
            PurchaseOrderItem poLine = invocation.getArgument(0);
            if (poLine.getPoItemId() == null) {
                poLine.setPoItemId((long) purchaseOrderItems.size() + 1);
                purchaseOrderItems.add(poLine);
            }
            return poLine;
        });
        when(purchaseOrderItemRepository.findByPoId(anyLong())).thenAnswer(invocation -> {
            Long poId = invocation.getArgument(0);
            return purchaseOrderItems.stream().filter(poLine -> poLine.getPoId().equals(poId)).toList();
        });
        when(purchaseOrderItemRepository.findByPoIdAndItemIdAndUnitId(anyLong(), anyLong(), anyLong())).thenAnswer(invocation -> {
            Long poId = invocation.getArgument(0);
            Long itemId = invocation.getArgument(1);
            Long unitId = invocation.getArgument(2);
            return purchaseOrderItems.stream()
                    .filter(poLine -> poLine.getPoId().equals(poId)
                            && poLine.getItemId().equals(itemId)
                            && poLine.getUnitId().equals(unitId))
                    .findFirst();
        });
        when(purchaseOrderItemRepository.findByPoIdAndItemIdAndVariantIdAndUnitId(anyLong(), anyLong(), any(), anyLong())).thenAnswer(invocation -> {
            Long poId = invocation.getArgument(0);
            Long itemId = invocation.getArgument(1);
            Long variantId = invocation.getArgument(2);
            Long unitId = invocation.getArgument(3);
            return purchaseOrderItems.stream()
                    .filter(poLine -> poLine.getPoId().equals(poId)
                            && poLine.getItemId().equals(itemId)
                            && java.util.Objects.equals(poLine.getVariantId(), variantId)
                            && poLine.getUnitId().equals(unitId))
                    .findFirst();
        });

        when(supplyRepository.save(any(Supply.class))).thenAnswer(invocation -> {
            Supply supply = invocation.getArgument(0);
            if (supply.getSupplyId() == null) {
                supply.setSupplyId(++supplySequence);
                supplies.add(supply);
            }
            return supply;
        });
        when(supplyRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long supplyId = invocation.getArgument(0);
            return supplies.stream().filter(supply -> supply.getSupplyId().equals(supplyId)).findFirst();
        });
        when(supplyRepository.findByPoId(anyLong())).thenAnswer(invocation -> {
            Long poId = invocation.getArgument(0);
            return supplies.stream().filter(supply -> poId.equals(supply.getPoId())).toList();
        });
        when(supplyProductRepository.findByInternalBatchBarcode(anyString())).thenReturn(Optional.empty());
        when(supplyProductRepository.save(any(SupplyProduct.class))).thenAnswer(invocation -> {
            SupplyProduct product = invocation.getArgument(0);
            if (product.getSupplyProductId() == null) {
                product.setSupplyProductId((long) supplyProducts.size() + 1);
                supplyProducts.add(product);
            }
            return product;
        });
        when(supplyProductRepository.findBySupplyId(anyLong())).thenAnswer(invocation -> {
            Long supplyId = invocation.getArgument(0);
            return supplyProducts.stream().filter(product -> product.getSupplyId().equals(supplyId)).toList();
        });

        when(supplierPaymentRepository.save(any(SupplierPayment.class))).thenAnswer(invocation -> {
            SupplierPayment payment = invocation.getArgument(0);
            if (payment.getSupplierPaymentId() == null) {
                payment.setSupplierPaymentId((long) supplierPayments.size() + 1);
                supplierPayments.add(payment);
            }
            return payment;
        });
        when(supplierPaymentRepository.findByPoId(anyLong())).thenAnswer(invocation -> {
            Long poId = invocation.getArgument(0);
            return supplierPayments.stream().filter(payment -> poId.equals(payment.getPoId())).toList();
        });
        when(supplierPaymentRepository.findBySupplyIdOrderByPaymentDateDesc(anyLong())).thenAnswer(invocation -> {
            Long supplyId = invocation.getArgument(0);
            return supplierPayments.stream()
                    .filter(payment -> supplyId.equals(payment.getSupplyId()))
                    .sorted(Comparator.comparing(SupplierPayment::getPaymentDate).reversed())
                    .toList();
        });
        when(supplierBalanceTransactionRepository.save(any(SupplierBalanceTransaction.class))).thenAnswer(invocation -> {
            SupplierBalanceTransaction transaction = invocation.getArgument(0);
            if (transaction.getTxnId() == null) {
                transaction.setTxnId((long) supplierLedger.size() + 1);
                supplierLedger.add(transaction);
            }
            return transaction;
        });
        when(orderRepository.findByCashSessionIdOrderByOrderDateDesc(anyLong())).thenReturn(List.of());
        when(paymentRepository.findByOrderId(anyLong())).thenReturn(List.of());
        when(cashSessionTransactionRepository.findBySessionIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
    }

    @Test
    void createPurchaseOrderStartsNotReceivedAndUnpaid() {
        PurchaseOrderResponseDto po = service.createPurchaseOrder(singleItemPo("PO-1", "100", "1000"));

        assertThat(po.getTotalAmount()).isEqualByComparingTo("100000");
        assertThat(po.getPaidAmount()).isEqualByComparingTo("0");
        assertThat(po.getBalanceAmount()).isEqualByComparingTo("100000");
        assertThat(po.getReceivingStatus()).isEqualTo("NOT_RECEIVED");
        assertThat(po.getPaymentStatus()).isEqualTo("UNPAID");
        assertThat(po.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void createPurchaseOrderAllowsSameItemAndUnitWhenVariantsDiffer() {
        PurchaseOrderRequestDto request = basePoRequest("PO-VARIANTS");
        request.setItems(List.of(
                poLine(ITEM_A_ID, VARIANT_A_ID, UNIT_A_ID, "40", "140"),
                poLine(ITEM_A_ID, VARIANT_B_ID, UNIT_A_ID, "30", "150")
        ));

        PurchaseOrderResponseDto po = service.createPurchaseOrder(request);

        assertThat(po.getItems()).hasSize(2);
        assertThat(po.getTotalAmount()).isEqualByComparingTo("10100");
        assertThat(po.getItems())
                .extracting(PurchaseOrderItemResponseDto::getVariantId)
                .containsExactlyInAnyOrder(VARIANT_A_ID, VARIANT_B_ID);
    }

    @Test
    void receivingPurchaseOrderTracksSameItemAndUnitByVariant() {
        PurchaseOrderRequestDto request = basePoRequest("PO-VARIANT-GRN");
        request.setItems(List.of(
                poLine(ITEM_A_ID, VARIANT_A_ID, UNIT_A_ID, "40", "140"),
                poLine(ITEM_A_ID, VARIANT_B_ID, UNIT_A_ID, "30", "150")
        ));
        PurchaseOrderResponseDto po = service.createPurchaseOrder(request);

        SupplyRequestDto receipt = supplyRequest(po.getPoId(), "0");
        receipt.setProducts(List.of(
                supplyLine(ITEM_A_ID, VARIANT_A_ID, UNIT_A_ID, "40", "140"),
                supplyLine(ITEM_A_ID, VARIANT_B_ID, UNIT_A_ID, "10", "150")
        ));
        service.createSupply(receipt);

        PurchaseOrderResponseDto current = service.getPurchaseOrderById(po.getPoId());
        assertThat(current.getReceivingStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(current.getItems())
                .filteredOn(item -> VARIANT_A_ID.equals(item.getVariantId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getReceivedQty()).isEqualByComparingTo("40");
                    assertThat(item.getRemainingQty()).isEqualByComparingTo("0");
                });
        assertThat(current.getItems())
                .filteredOn(item -> VARIANT_B_ID.equals(item.getVariantId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getReceivedQty()).isEqualByComparingTo("10");
                    assertThat(item.getRemainingQty()).isEqualByComparingTo("20");
                });
    }

    @Test
    void advancePaymentsDoNotChangeReceivingStatus() {
        PurchaseOrderResponseDto po = service.createPurchaseOrder(singleItemPo("PO-ADV", "100", "1000"));

        payPo(po.getPoId(), "50000");
        PurchaseOrderResponseDto partiallyPaid = service.getPurchaseOrderById(po.getPoId());
        assertThat(partiallyPaid.getReceivingStatus()).isEqualTo("NOT_RECEIVED");
        assertThat(partiallyPaid.getPaymentStatus()).isEqualTo("PARTIAL");
        assertThat(partiallyPaid.getPaidAmount()).isEqualByComparingTo("50000");

        payPo(po.getPoId(), "50000");
        PurchaseOrderResponseDto fullyPaid = service.getPurchaseOrderById(po.getPoId());
        assertThat(fullyPaid.getReceivingStatus()).isEqualTo("NOT_RECEIVED");
        assertThat(fullyPaid.getPaymentStatus()).isEqualTo("PAID");
        assertThat(fullyPaid.getBalanceAmount()).isEqualByComparingTo("0");
    }

    @Test
    void supplierCashPaymentCannotExceedAvailableCashDrawerAmount() {
        CashSession session = openCashSession("5000.00");
        when(cashSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        SupplierPaymentRequestDto request = paymentRequest("5001.00");
        request.setPaymentMethod("CASH");
        request.setCashSessionId(10L);

        assertThatThrownBy(() -> service.createSupplierPayment(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("exceeds available cash");
    }

    @Test
    void supplierCashPaymentUsesCorrectedCashSalesWhenCheckingDrawerAvailability() {
        CashSession session = openCashSession("6200.00");
        when(cashSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        CustomerOrder order = new CustomerOrder();
        order.setOrderId(77L);
        order.setInvoiceNo("INV-77");
        order.setCashSessionId(10L);
        order.setStatus("COMPLETED");
        order.setTotal(new BigDecimal("300.00"));

        Payment payment = new Payment();
        payment.setOrderId(77L);
        payment.setCashSessionId(10L);
        payment.setPaymentMethod("CASH");
        payment.setAmount(new BigDecimal("500.00"));
        payment.setTenderedAmount(new BigDecimal("500.00"));

        when(orderRepository.findByCashSessionIdOrderByOrderDateDesc(10L)).thenReturn(List.of(order));
        when(paymentRepository.findByOrderId(77L)).thenReturn(List.of(payment));

        CashSessionTransaction supplierOut = new CashSessionTransaction();
        supplierOut.setSessionId(10L);
        supplierOut.setType("SUPPLIER_PAYMENT_OUT");
        supplierOut.setAmount(new BigDecimal("6000.00"));
        supplierOut.setPaymentMethod("CASH");
        when(cashSessionTransactionRepository.findBySessionIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(supplierOut));

        SupplierPaymentRequestDto request = paymentRequest("500.00");
        request.setPaymentMethod("CASH");
        request.setCashSessionId(10L);

        service.createSupplierPayment(request);

        assertThat(supplierPayments).hasSize(1);
        assertThat(supplierPayments.get(0).getAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void multiplePartialReceiptsUpdateOneGrnAndAccumulateReceivedQuantities() {
        PurchaseOrderResponseDto po = service.createPurchaseOrder(singleItemPo("PO-GRN", "100", "1000"));

        receivePo(po.getPoId(), "40", "0");
        PurchaseOrderResponseDto firstReceipt = service.getPurchaseOrderById(po.getPoId());
        assertThat(firstReceipt.getReceivingStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(firstReceipt.getItems().get(0).getReceivedQty()).isEqualByComparingTo("40");
        assertThat(firstReceipt.getItems().get(0).getRemainingQty()).isEqualByComparingTo("60");
        assertThat(supplies).hasSize(1);
        Long grnId = supplies.get(0).getSupplyId();

        receivePo(po.getPoId(), "30", "0");
        PurchaseOrderResponseDto secondReceipt = service.getPurchaseOrderById(po.getPoId());
        assertThat(secondReceipt.getReceivingStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(secondReceipt.getItems().get(0).getReceivedQty()).isEqualByComparingTo("70");
        assertThat(supplies).hasSize(1);
        assertThat(supplies.get(0).getSupplyId()).isEqualTo(grnId);

        receivePo(po.getPoId(), "30", "0");
        PurchaseOrderResponseDto fullReceipt = service.getPurchaseOrderById(po.getPoId());
        assertThat(fullReceipt.getReceivingStatus()).isEqualTo("FULLY_RECEIVED");
        assertThat(fullReceipt.getPaymentStatus()).isEqualTo("UNPAID");
        assertThat(fullReceipt.getItems().get(0).getReceivedQty()).isEqualByComparingTo("100");
        assertThat(fullReceipt.getStatus()).isEqualTo("COMPLETED");
        assertThat(supplies).hasSize(1);
        assertThat(supplyProducts)
                .filteredOn(product -> product.getSupplyId().equals(grnId))
                .hasSize(3);
        SupplyResponseDto grn = service.getSupplyById(grnId);
        assertThat(grn.getTotal()).isEqualByComparingTo("100000");
        assertThat(grn.getProducts()).hasSize(3);
    }

    @Test
    void paymentCanRemainPartialAfterFullReceivingAndBeCompletedLater() {
        PurchaseOrderResponseDto po = service.createPurchaseOrder(singleItemPo("PO-FULL-REC", "100", "1000"));
        receivePo(po.getPoId(), "100", "0");
        Long supplyId = supplies.get(0).getSupplyId();

        paySupply(supplyId, "50000");
        PurchaseOrderResponseDto partiallyPaid = service.getPurchaseOrderById(po.getPoId());
        assertThat(partiallyPaid.getReceivingStatus()).isEqualTo("FULLY_RECEIVED");
        assertThat(partiallyPaid.getPaymentStatus()).isEqualTo("PARTIAL");
        assertThat(partiallyPaid.getBalanceAmount()).isEqualByComparingTo("50000");

        paySupply(supplyId, "50000");
        PurchaseOrderResponseDto fullySettled = service.getPurchaseOrderById(po.getPoId());
        assertThat(fullySettled.getReceivingStatus()).isEqualTo("FULLY_RECEIVED");
        assertThat(fullySettled.getPaymentStatus()).isEqualTo("PAID");
        assertThat(fullySettled.getPaidAmount()).isEqualByComparingTo("100000");
    }

    @Test
    void paidPartialReceiptsAccumulateOnTheSameGrn() {
        PurchaseOrderResponseDto po = service.createPurchaseOrder(singleItemPo("PO-GRN-PAID", "80", "110"));

        SupplyRequestDto firstReceipt = supplyRequest(po.getPoId(), "4400");
        firstReceipt.setProducts(List.of(supplyLine(ITEM_A_ID, UNIT_A_ID, "40", "110")));
        service.createSupply(firstReceipt);
        Long grnId = supplies.get(0).getSupplyId();

        SupplyRequestDto secondReceipt = supplyRequest(po.getPoId(), "4400");
        secondReceipt.setProducts(List.of(supplyLine(ITEM_A_ID, UNIT_A_ID, "40", "110")));
        service.createSupply(secondReceipt);

        assertThat(supplies).hasSize(1);
        assertThat(supplies.get(0).getSupplyId()).isEqualTo(grnId);
        assertThat(supplierPayments)
                .filteredOn(payment -> grnId.equals(payment.getSupplyId()))
                .hasSize(2);

        SupplyResponseDto grn = service.getSupplyById(grnId);
        assertThat(grn.getTotal()).isEqualByComparingTo("8800");
        assertThat(grn.getPaidAmount()).isEqualByComparingTo("8800");
        assertThat(grn.getBalanceAmount()).isEqualByComparingTo("0");
        assertThat(grn.getPaymentStatus()).isEqualTo("PAID");

        PurchaseOrderResponseDto current = service.getPurchaseOrderById(po.getPoId());
        assertThat(current.getReceivingStatus()).isEqualTo("FULLY_RECEIVED");
        assertThat(current.getPaymentStatus()).isEqualTo("PAID");
        assertThat(current.getItems().get(0).getReceivedQty()).isEqualByComparingTo("80");
    }

    @Test
    void oneToOneGrnReflectsPurchaseOrderPaymentsWhenSettledFromPo() {
        PurchaseOrderResponseDto po = service.createPurchaseOrder(singleItemPo("PO-GRN-PAID-FROM-PO", "100", "1000"));
        receivePo(po.getPoId(), "100", "50000");
        Long supplyId = supplies.get(0).getSupplyId();

        payPo(po.getPoId(), "50000");

        SupplyResponseDto grn = service.getSupplyById(supplyId);
        assertThat(grn.getPaidAmount()).isEqualByComparingTo("100000");
        assertThat(grn.getBalanceAmount()).isEqualByComparingTo("0");
        assertThat(grn.getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void fullyPaidPurchaseOrderCanRemainPartiallyReceived() {
        PurchaseOrderResponseDto po = service.createPurchaseOrder(singleItemPo("PO-PAID-FIRST", "100", "1000"));

        payPo(po.getPoId(), "100000");
        receivePo(po.getPoId(), "40", "0");

        PurchaseOrderResponseDto current = service.getPurchaseOrderById(po.getPoId());
        assertThat(current.getPaymentStatus()).isEqualTo("PAID");
        assertThat(current.getReceivingStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(current.getItems().get(0).getRemainingQty()).isEqualByComparingTo("60");
    }

    @Test
    void receivingStatusUsesEveryPurchaseOrderLine() {
        PurchaseOrderResponseDto po = service.createPurchaseOrder(twoItemPo("PO-MULTI"));

        SupplyRequestDto request = supplyRequest(po.getPoId(), "0");
        request.setProducts(List.of(
                supplyLine(ITEM_A_ID, UNIT_A_ID, "10", "1000"),
                supplyLine(ITEM_B_ID, UNIT_B_ID, "5", "1000")
        ));
        service.createSupply(request);

        PurchaseOrderResponseDto current = service.getPurchaseOrderById(po.getPoId());
        assertThat(current.getReceivingStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(current.getItems())
                .extracting(item -> item.getRemainingQty().stripTrailingZeros().toPlainString())
                .containsExactly("0", "15");
    }

    @Test
    void preventsOverReceivingAgainstRemainingPurchaseOrderQuantity() {
        PurchaseOrderResponseDto po = service.createPurchaseOrder(singleItemPo("PO-OVER-REC", "100", "1000"));
        receivePo(po.getPoId(), "80", "0");

        assertThatThrownBy(() -> receivePo(po.getPoId(), "30", "0"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Received quantity exceeds remaining purchase order quantity");
    }

    @Test
    void preventsOverpaymentAgainstPurchaseOrderBalance() {
        PurchaseOrderResponseDto po = service.createPurchaseOrder(singleItemPo("PO-OVER-PAY", "100", "1000"));
        payPo(po.getPoId(), "80000");

        assertThatThrownBy(() -> payPo(po.getPoId(), "30000"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Payment amount exceeds purchase order balance");
    }

    @Test
    void supportsCompleteReceivingAndPaymentMatrix() {
        assertMatrix("0", "0", "NOT_RECEIVED", "UNPAID");
        assertMatrix("0", "50000", "NOT_RECEIVED", "PARTIAL");
        assertMatrix("0", "100000", "NOT_RECEIVED", "PAID");
        assertMatrix("50", "0", "PARTIALLY_RECEIVED", "UNPAID");
        assertMatrix("50", "50000", "PARTIALLY_RECEIVED", "PARTIAL");
        assertMatrix("50", "100000", "PARTIALLY_RECEIVED", "PAID");
        assertMatrix("100", "0", "FULLY_RECEIVED", "UNPAID");
        assertMatrix("100", "50000", "FULLY_RECEIVED", "PARTIAL");
        assertMatrix("100", "100000", "FULLY_RECEIVED", "PAID");
    }

    private void assertMatrix(
            String receivedQty,
            String paidAmount,
            String expectedReceivingStatus,
            String expectedPaymentStatus
    ) {
        PurchaseOrderResponseDto po = service.createPurchaseOrder(
                singleItemPo("PO-MATRIX-" + purchaseOrders.size(), "100", "1000")
        );

        if (new BigDecimal(receivedQty).compareTo(BigDecimal.ZERO) > 0) {
            receivePo(po.getPoId(), receivedQty, "0");
        }

        if (new BigDecimal(paidAmount).compareTo(BigDecimal.ZERO) > 0) {
            payPo(po.getPoId(), paidAmount);
        }

        PurchaseOrderResponseDto current = service.getPurchaseOrderById(po.getPoId());
        assertThat(current.getReceivingStatus()).isEqualTo(expectedReceivingStatus);
        assertThat(current.getPaymentStatus()).isEqualTo(expectedPaymentStatus);
    }

    private void stubCatalogItem(Long itemId, Long unitId, String name) {
        Item item = new Item();
        item.setItemId(itemId);
        item.setBranchId(BRANCH_ID);
        item.setName(name);
        item.setSku("SKU-" + itemId);
        item.setIsActive(true);

        ItemUnit unit = new ItemUnit();
        unit.setUnitId(unitId);
        unit.setBranchId(BRANCH_ID);
        unit.setItemId(itemId);
        unit.setUnitName("Pcs");
        unit.setMultiplierToBase(BigDecimal.ONE);
        unit.setBarcode("BAR-" + itemId);
        unit.setIsBaseUnit(true);
        unit.setIsActive(true);

        when(itemRepository.findByItemIdAndIsActiveTrue(itemId)).thenReturn(Optional.of(item));
        when(itemUnitRepository.findByItemIdAndUnitIdAndIsActiveTrue(itemId, unitId)).thenReturn(Optional.of(unit));
        when(itemUnitRepository.findByItemIdAndIsBaseUnitTrue(itemId)).thenReturn(Optional.of(unit));
    }

    private void stubCatalogVariant(Long itemId, Long variantId, String sku) {
        ItemVariant variant = new ItemVariant();
        variant.setVariantId(variantId);
        variant.setItemId(itemId);
        variant.setBranchId(BRANCH_ID);
        variant.setSku(sku);
        variant.setIsActive(true);

        when(itemVariantRepository.findByVariantIdAndItemId(variantId, itemId)).thenReturn(Optional.of(variant));
        when(itemVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(itemVariantAttributeRepository.findByVariantIdOrderByAttributeNameAsc(variantId)).thenReturn(List.of());
    }

    private SupplierItem activeSupplierItem(Long branchId, Long supplierId, Long itemId, Long unitId) {
        return activeSupplierItem(branchId, supplierId, itemId, null, unitId);
    }

    private SupplierItem activeSupplierItem(Long branchId, Long supplierId, Long itemId, Long variantId, Long unitId) {
        SupplierItem supplierItem = new SupplierItem();
        supplierItem.setBranchId(branchId);
        supplierItem.setSupplierId(supplierId);
        supplierItem.setItemId(itemId);
        supplierItem.setVariantId(variantId);
        supplierItem.setUnitId(unitId);
        supplierItem.setIsActive(true);
        return supplierItem;
    }

    private PurchaseOrderRequestDto singleItemPo(String poNo, String qty, String unitCost) {
        PurchaseOrderRequestDto request = basePoRequest(poNo);
        request.setItems(List.of(poLine(ITEM_A_ID, UNIT_A_ID, qty, unitCost)));
        return request;
    }

    private PurchaseOrderRequestDto twoItemPo(String poNo) {
        PurchaseOrderRequestDto request = basePoRequest(poNo);
        request.setItems(List.of(
                poLine(ITEM_A_ID, UNIT_A_ID, "10", "1000"),
                poLine(ITEM_B_ID, UNIT_B_ID, "20", "1000")
        ));
        return request;
    }

    private PurchaseOrderRequestDto basePoRequest(String poNo) {
        PurchaseOrderRequestDto request = new PurchaseOrderRequestDto();
        request.setBranchId(BRANCH_ID);
        request.setSupplierId(SUPPLIER_ID);
        request.setPoNo(poNo);
        request.setExpectedDate(LocalDate.of(2026, 8, 26));
        request.setCreatedBy(USER_ID);
        return request;
    }

    private PurchaseOrderItemRequestDto poLine(Long itemId, Long unitId, String qty, String unitCost) {
        PurchaseOrderItemRequestDto item = new PurchaseOrderItemRequestDto();
        item.setItemId(itemId);
        item.setUnitId(unitId);
        item.setOrderedQty(new BigDecimal(qty));
        item.setUnitCostEst(new BigDecimal(unitCost));
        return item;
    }

    private PurchaseOrderItemRequestDto poLine(Long itemId, Long variantId, Long unitId, String qty, String unitCost) {
        PurchaseOrderItemRequestDto item = poLine(itemId, unitId, qty, unitCost);
        item.setVariantId(variantId);
        return item;
    }

    private void receivePo(Long poId, String qty, String paidAmount) {
        service.createSupply(supplyRequest(poId, qty, paidAmount));
    }

    private SupplyRequestDto supplyRequest(Long poId, String qty, String paidAmount) {
        SupplyRequestDto request = supplyRequest(poId, paidAmount);
        request.setProducts(List.of(supplyLine(ITEM_A_ID, UNIT_A_ID, qty, "1000")));
        return request;
    }

    private SupplyRequestDto supplyRequest(Long poId, String paidAmount) {
        SupplyRequestDto request = new SupplyRequestDto();
        request.setBranchId(BRANCH_ID);
        request.setSupplierId(SUPPLIER_ID);
        request.setPoId(poId);
        request.setInvoiceNo("INV-" + supplies.size());
        request.setPaidAmount(new BigDecimal(paidAmount));
        request.setPaymentMethod("BANK_TRANSFER");
        request.setReceivedBy(USER_ID);
        return request;
    }

    private SupplyProductRequestDto supplyLine(Long itemId, Long unitId, String qty, String unitCost) {
        SupplyProductRequestDto product = new SupplyProductRequestDto();
        product.setItemId(itemId);
        product.setUnitId(unitId);
        product.setCostPrice(new BigDecimal(unitCost));
        product.setSellingPrice(BigDecimal.ZERO);
        product.setQuantityReceived(new BigDecimal(qty));
        product.setLineTotal(new BigDecimal(unitCost).multiply(new BigDecimal(qty)));
        return product;
    }

    private SupplyProductRequestDto supplyLine(Long itemId, Long variantId, Long unitId, String qty, String unitCost) {
        SupplyProductRequestDto product = supplyLine(itemId, unitId, qty, unitCost);
        product.setVariantId(variantId);
        return product;
    }

    private void payPo(Long poId, String amount) {
        SupplierPaymentRequestDto request = paymentRequest(amount);
        request.setPoId(poId);
        service.createSupplierPayment(request);
    }

    private void paySupply(Long supplyId, String amount) {
        SupplierPaymentRequestDto request = paymentRequest(amount);
        request.setSupplyId(supplyId);
        service.createSupplierPayment(request);
    }

    private CashSession openCashSession(String openingCash) {
        CashSession session = new CashSession();
        session.setSessionId(10L);
        session.setCounterId(1L);
        session.setOpenedBy(USER_ID);
        session.setOpeningCash(new BigDecimal(openingCash));
        session.setStatus("OPEN");
        return session;
    }

    private SupplierPaymentRequestDto paymentRequest(String amount) {
        SupplierPaymentRequestDto request = new SupplierPaymentRequestDto();
        request.setBranchId(BRANCH_ID);
        request.setSupplierId(SUPPLIER_ID);
        request.setAmount(new BigDecimal(amount));
        request.setPaymentMethod("BANK_TRANSFER");
        request.setPaidBy(USER_ID);
        request.setReferenceNo("BANK-" + supplierPayments.size());
        return request;
    }
}
