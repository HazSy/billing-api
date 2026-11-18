package dev.hazsy.billing.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Cálculo de importes sin depender de Spring.
 *
 * Decisión: el IVA se redondea <strong>por línea</strong> y después se suma.
 * Es lo que exige la facturación española (cada línea lleva su cuota) y evita
 * que el total dependa del orden de las líneas.
 */
public final class InvoiceCalculator {

    public static final int MONEY_SCALE = 2;
    public static final int QUANTITY_SCALE = 3;

    private InvoiceCalculator() {
    }

    public record Line(BigDecimal quantity, BigDecimal unitPrice, BigDecimal vatRate) {
    }

    public record Totals(BigDecimal net, BigDecimal vat, BigDecimal gross) {
    }

    public static BigDecimal lineNet(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineVat(BigDecimal net, BigDecimal vatRate) {
        return net.multiply(vatRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static Totals totals(List<Line> lines) {
        BigDecimal net = BigDecimal.ZERO.setScale(MONEY_SCALE);
        BigDecimal vat = BigDecimal.ZERO.setScale(MONEY_SCALE);

        for (Line line : lines) {
            BigDecimal lineNet = lineNet(normalizeQuantity(line.quantity()), line.unitPrice());
            net = net.add(lineNet);
            vat = vat.add(lineVat(lineNet, line.vatRate()));
        }

        return new Totals(net, vat, net.add(vat));
    }

    private static BigDecimal normalizeQuantity(BigDecimal quantity) {
        return quantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }
}
