package dev.hazsy.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BillingApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private String bearer() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin12345"}"""))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        return "Bearer " + body.get("accessToken").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("sin token la API responde 401")
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("las credenciales incorrectas no emiten token")
    void rejectsWrongCredentials() throws Exception {
        mvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"no-es-la-buena"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("flujo completo: cliente, factura, emisión, pago idempotente y liquidación")
    void fullBillingFlow() throws Exception {
        String auth = bearer();

        JsonNode customer = json(mvc.perform(post("/api/customers")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme SL","email":"facturas@acme.es","taxId":"B12345678"}"""))
                .andExpect(status().isCreated())
                .andReturn());

        String customerId = customer.get("id").asText();

        JsonNode invoice = json(mvc.perform(post("/api/invoices")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId":"%s",
                                  "currency":"EUR",
                                  "lines":[
                                    {"description":"Consultoría","quantity":2,"unitPrice":50.00,"vatRate":0.21},
                                    {"description":"Licencia","quantity":1,"unitPrice":30.00,"vatRate":0.10}
                                  ]
                                }""".formatted(customerId)))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(invoice.get("number").asText()).startsWith("INV-");
        assertThat(invoice.get("netTotal").decimalValue()).isEqualByComparingTo(new BigDecimal("130.00"));
        assertThat(invoice.get("vatTotal").decimalValue()).isEqualByComparingTo(new BigDecimal("24.00"));
        assertThat(invoice.get("grossTotal").decimalValue()).isEqualByComparingTo(new BigDecimal("154.00"));

        String invoiceId = invoice.get("id").asText();

        mvc.perform(post("/api/invoices/{id}/issue", invoiceId).header("Authorization", auth))
                .andExpect(status().isOk());

        JsonNode partial = json(mvc.perform(post("/api/invoices/{id}/payments", invoiceId)
                        .header("Authorization", auth)
                        .header("Idempotency-Key", "pago-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100.00}"""))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(partial.get("invoiceStatus").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(partial.get("replayed").asBoolean()).isFalse();

        // Misma clave, misma factura: no se vuelve a cobrar.
        JsonNode replay = json(mvc.perform(post("/api/invoices/{id}/payments", invoiceId)
                        .header("Authorization", auth)
                        .header("Idempotency-Key", "pago-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100.00}"""))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(replay.get("replayed").asBoolean()).isTrue();

        JsonNode afterReplay = json(mvc.perform(get("/api/invoices/{id}", invoiceId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(afterReplay.get("paidTotal").decimalValue()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(afterReplay.get("outstanding").decimalValue()).isEqualByComparingTo(new BigDecimal("54.00"));

        JsonNode settled = json(mvc.perform(post("/api/invoices/{id}/payments", invoiceId)
                        .header("Authorization", auth)
                        .header("Idempotency-Key", "pago-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":54.00}"""))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(settled.get("invoiceStatus").asText()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("un pago superior al total devuelve 400 con Problem Details")
    void rejectsOverpayment() throws Exception {
        String auth = bearer();

        JsonNode customer = json(mvc.perform(post("/api/customers")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Otra SL","email":"otra@acme.es","taxId":"B87654321"}"""))
                .andExpect(status().isCreated())
                .andReturn());

        JsonNode invoice = json(mvc.perform(post("/api/invoices")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","currency":"EUR",
                                 "lines":[{"description":"Servicio","quantity":1,"unitPrice":10.00,"vatRate":0.21}]}"""
                                .formatted(customer.get("id").asText())))
                .andExpect(status().isCreated())
                .andReturn());

        String invoiceId = invoice.get("id").asText();
        mvc.perform(post("/api/invoices/{id}/issue", invoiceId).header("Authorization", auth))
                .andExpect(status().isOk());

        JsonNode error = json(mvc.perform(post("/api/invoices/{id}/payments", invoiceId)
                        .header("Authorization", auth)
                        .header("Idempotency-Key", "pago-exceso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":500.00}"""))
                .andExpect(status().isBadRequest())
                .andReturn());

        assertThat(error.get("code").asText()).isEqualTo("overpayment");
    }

    @Test
    @DisplayName("emitir dos veces devuelve 409")
    void cannotIssueTwice() throws Exception {
        String auth = bearer();

        JsonNode customer = json(mvc.perform(post("/api/customers")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tercera SL","email":"tercera@acme.es","taxId":"B11112222"}"""))
                .andExpect(status().isCreated())
                .andReturn());

        JsonNode invoice = json(mvc.perform(post("/api/invoices")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","currency":"EUR",
                                 "lines":[{"description":"Servicio","quantity":1,"unitPrice":10.00,"vatRate":0.21}]}"""
                                .formatted(customer.get("id").asText())))
                .andExpect(status().isCreated())
                .andReturn());

        String invoiceId = invoice.get("id").asText();

        mvc.perform(post("/api/invoices/{id}/issue", invoiceId).header("Authorization", auth))
                .andExpect(status().isOk());
        mvc.perform(post("/api/invoices/{id}/issue", invoiceId).header("Authorization", auth))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("una factura inexistente devuelve 404")
    void unknownInvoiceReturnsNotFound() throws Exception {
        mvc.perform(get("/api/invoices/{id}", UUID.randomUUID()).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }
}
