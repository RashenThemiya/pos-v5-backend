package com.pos.system.service;

import com.pos.system.model.sale.CustomerOrder;
import com.pos.system.model.sale.OrderProduct;
import com.pos.system.model.sale.SalesReturnItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Per-line view of what an order's lines are still worth to a return.
 *
 * <p>Refundable value comes from what was actually paid. That is the line total after
 * line promotions, minus the line's proportional share of bill-level discount and rounding.
 * Prior returns reduce both the returnable quantity and the remaining refundable value.
 * The ledger never trusts a client-supplied price.
 */
final class SalesReturnLedger {

    private static final int MONEY_SCALE = 2;

    private final Map<Long, Line> lines = new LinkedHashMap<>();

    private SalesReturnLedger() {
    }

    static SalesReturnLedger build(CustomerOrder order, List<OrderProduct> products, List<SalesReturnItem> priorItems) {
        SalesReturnLedger ledger = new SalesReturnLedger();
        allocateNetTotals(order, products).forEach((product, net) ->
                ledger.lines.put(product.getOrderProductId(), new Line(product, net)));
        ledger.applyPriorReturns(priorItems);
        return ledger;
    }

    List<Line> lines() {
        return List.copyOf(lines.values());
    }

    Line line(Long orderProductId) {
        return lines.get(orderProductId);
    }

    /**
     * Reserves {@code quantity} of the matching order line(s) and returns the allocations with their refunds.
     * Throws if the order cannot cover the quantity. Reservations accumulate, so one request can't
     * get past the limit by repeating a line.
     */
    List<Allocation> reserve(Long orderProductId, Long itemId, Long variantId, String batchBarcode, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Return quantity must be greater than zero");
        }
        List<Line> candidates;
        if (orderProductId != null) {
            Line line = lines.get(orderProductId);
            if (line == null || !Objects.equals(line.product.getItemId(), itemId)) {
                throw new IllegalArgumentException("Return line does not belong to this order: " + orderProductId);
            }
            candidates = List.of(line);
        } else {
            candidates = matching(itemId, variantId, batchBarcode);
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("Item " + itemId + " was not sold on this order");
            }
        }

        BigDecimal available = candidates.stream().map(Line::remainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(quantity) < 0) {
            throw new IllegalArgumentException("Return quantity " + quantity.stripTrailingZeros().toPlainString()
                    + " for item " + itemId + " exceeds the returnable quantity "
                    + available.stripTrailingZeros().toPlainString());
        }

        List<Allocation> allocations = new ArrayList<>();
        BigDecimal outstanding = quantity;
        for (Line line : candidates) {
            if (outstanding.signum() == 0) break;
            BigDecimal take = outstanding.min(line.remainingQuantity());
            if (take.signum() <= 0) continue;
            BigDecimal refund = line.refundFor(take);
            line.returnedQuantity = line.returnedQuantity.add(take);
            line.refundedAmount = line.refundedAmount.add(refund);
            allocations.add(new Allocation(line.product, take, refund));
            outstanding = outstanding.subtract(take);
        }
        return allocations;
    }

    // ── internals ────────────────────────────────────────────────────────────

    /** Spreads the order total over its lines in proportion to line totals; the last line absorbs rounding. */
    private static Map<OrderProduct, BigDecimal> allocateNetTotals(CustomerOrder order, List<OrderProduct> products) {
        Map<OrderProduct, BigDecimal> result = new LinkedHashMap<>();
        BigDecimal subtotal = products.stream().map(SalesReturnLedger::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderTotal = order.getTotal() != null ? order.getTotal() : subtotal;
        if (subtotal.signum() <= 0 || orderTotal.signum() <= 0) {
            products.forEach(p -> result.put(p, BigDecimal.ZERO.setScale(MONEY_SCALE)));
            return result;
        }
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < products.size(); i++) {
            OrderProduct product = products.get(i);
            BigDecimal net = i == products.size() - 1
                    ? orderTotal.subtract(allocated)
                    : lineTotal(product).multiply(orderTotal).divide(subtotal, MONEY_SCALE, RoundingMode.HALF_UP);
            net = net.max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            allocated = allocated.add(net);
            result.put(product, net);
        }
        return result;
    }

    private static BigDecimal lineTotal(OrderProduct product) {
        if (product.getLineTotal() != null) return product.getLineTotal();
        BigDecimal gross = nvl(product.getUnitPrice()).multiply(nvl(product.getQuantity()));
        return gross.subtract(nvl(product.getDiscount())).max(BigDecimal.ZERO);
    }

    private void applyPriorReturns(List<SalesReturnItem> priorItems) {
        for (SalesReturnItem prior : priorItems) {
            BigDecimal qty = nvl(prior.getQuantity());
            BigDecimal refund = nvl(prior.getLineRefund());
            Line linked = prior.getOrderProductId() != null ? lines.get(prior.getOrderProductId()) : null;
            if (linked != null) {
                linked.returnedQuantity = linked.returnedQuantity.add(qty);
                linked.refundedAmount = linked.refundedAmount.add(refund);
                continue;
            }
            // Legacy rows predate the order-line link: attribute them by item/variant/batch.
            List<Line> candidates = matching(prior.getItemId(), prior.getVariantId(), prior.getInternalBatchBarcode());
            if (candidates.isEmpty()) continue;
            BigDecimal outstanding = qty;
            for (int i = 0; i < candidates.size() && outstanding.signum() > 0; i++) {
                Line line = candidates.get(i);
                boolean last = i == candidates.size() - 1;
                BigDecimal take = last ? outstanding : outstanding.min(line.remainingQuantity());
                if (take.signum() <= 0) continue;
                BigDecimal share = qty.signum() == 0 ? BigDecimal.ZERO
                        : refund.multiply(take).divide(qty, MONEY_SCALE, RoundingMode.HALF_UP);
                line.returnedQuantity = line.returnedQuantity.add(take);
                line.refundedAmount = line.refundedAmount.add(share);
                outstanding = outstanding.subtract(take);
            }
        }
    }

    private List<Line> matching(Long itemId, Long variantId, String batchBarcode) {
        String batch = blankToNull(batchBarcode);
        return lines.values().stream()
                .filter(line -> Objects.equals(line.product.getItemId(), itemId))
                .filter(line -> Objects.equals(line.product.getVariantId(), variantId))
                .filter(line -> batch == null || line.batch() == null || batch.equals(line.batch()))
                // lines sold from exactly this batch come before FIFO lines
                .sorted(Comparator.comparing(line -> batch != null && batch.equals(line.batch()) ? 0 : 1))
                .toList();
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    // ── value types ──────────────────────────────────────────────────────────

    static final class Line {
        private final OrderProduct product;
        private final BigDecimal netTotal;
        private BigDecimal returnedQuantity = BigDecimal.ZERO;
        private BigDecimal refundedAmount = BigDecimal.ZERO;

        private Line(OrderProduct product, BigDecimal netTotal) {
            this.product = product;
            this.netTotal = netTotal;
        }

        OrderProduct product() { return product; }
        BigDecimal netTotal() { return netTotal; }
        BigDecimal soldQuantity() { return nvl(product.getQuantity()); }
        BigDecimal returnedQuantity() { return returnedQuantity; }
        BigDecimal refundedAmount() { return refundedAmount; }

        BigDecimal remainingQuantity() {
            return soldQuantity().subtract(returnedQuantity).max(BigDecimal.ZERO);
        }

        BigDecimal refundableAmount() {
            return netTotal.subtract(refundedAmount).max(BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        /** Net paid value per sold unit, for display only; refunds come from {@link #refundFor}. */
        BigDecimal netUnitPrice() {
            BigDecimal sold = soldQuantity();
            return sold.signum() == 0 ? BigDecimal.ZERO.setScale(MONEY_SCALE)
                    : netTotal.divide(sold, MONEY_SCALE, RoundingMode.HALF_UP);
        }

        /**
         * Refund for returning {@code qty} more units. Partial returns pay a pro-rata share of the net line value.
         * The return that closes the line pays exactly what is left, so the total refund always equals the
         * amount paid and never exceeds it.
         */
        BigDecimal refundFor(BigDecimal qty) {
            BigDecimal sold = soldQuantity();
            if (sold.signum() <= 0) return BigDecimal.ZERO.setScale(MONEY_SCALE);
            BigDecimal remainingValue = refundableAmount();
            BigDecimal afterQty = returnedQuantity.add(qty);
            if (afterQty.compareTo(sold) >= 0) {
                return remainingValue;
            }
            BigDecimal cumulativeAfter = netTotal.multiply(afterQty).divide(sold, MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal cumulativeBefore = netTotal.multiply(returnedQuantity).divide(sold, MONEY_SCALE, RoundingMode.HALF_UP);
            return cumulativeAfter.subtract(cumulativeBefore).max(BigDecimal.ZERO).min(remainingValue);
        }

        private String batch() {
            return blankToNull(product.getBatchBarcode());
        }
    }

    record Allocation(OrderProduct product, BigDecimal quantity, BigDecimal refund) {
        BigDecimal unitRefund() {
            return quantity.signum() == 0 ? BigDecimal.ZERO.setScale(MONEY_SCALE)
                    : refund.divide(quantity, MONEY_SCALE, RoundingMode.HALF_UP);
        }
    }
}
