package dev.hazsy.billing.invoice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceCalculatorTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("el importe de una línea se redondea a dos decimales (HALF_UP)")
    void lineNetRoundsHalfUp() {
        assertThat(InvoiceCalculator.lineNet(money("3"), money("0.335")))
                .isEqualByComparingTo("1.01");
    }

    @Test
    @DisplayName("el IVA se calcula sobre el neto ya redondeado")
    void lineVatUsesRoundedNet() {
        BigDecimal net = InvoiceCalculator.lineNet(money("1"), money("9.99"));
        assertThat(InvoiceCalculator.lineVat(net, money("0.21")))
                .isEqualByComparingTo("2.10");
    }

    @Test
    @DisplayName("suma neto, IVA y total de varias líneas con tipos distintos")
    void totalsForMixedVatRates() {
        InvoiceCalculator.Totals totals = InvoiceCalculator.totals(List.of(
                new InvoiceCalculator.Line(money("2"), money("50.00"), money("0.21")),
                new InvoiceCalculator.Line(money("1"), money("30.00"), money("0.10"))
        ));

        assertThat(totals.net()).isEqualByComparingTo("130.00");
        assertThat(totals.vat()).isEqualByComparingTo("24.00");
        assertThat(totals.gross()).isEqualByComparingTo("154.00");
    }

    @Test
    @DisplayName("el total no depende del orden de las líneas")
    void totalsAreOrderIndependent() {
        var a = new InvoiceCalculator.Line(money("1"), money("0.335"), money("0.21"));
        var b = new InvoiceCalculator.Line(money("7"), money("1.115"), money("0.04"));

        assertThat(InvoiceCalculator.totals(List.of(a, b)))
                .isEqualTo(InvoiceCalculator.totals(List.of(b, a)));
    }

    @Test
    @DisplayName("una cantidad con más de tres decimales se normaliza antes de multiplicar")
    void quantityIsNormalizedToThreeDecimals() {
        assertThat(InvoiceCalculator.lineNet(money("1.0005"), money("10.00")))
                .isEqualByComparingTo("10.01");
    }

    @Test
    @DisplayName("una factura sin líneas suma cero, no null")
    void emptyInvoiceTotalsAreZero() {
        InvoiceCalculator.Totals totals = InvoiceCalculator.totals(List.of());

        assertThat(totals.net()).isEqualByComparingTo("0.00");
        assertThat(totals.vat()).isEqualByComparingTo("0.00");
        assertThat(totals.gross()).isEqualByComparingTo("0.00");
    }
}
