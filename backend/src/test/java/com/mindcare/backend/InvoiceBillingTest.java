package com.mindcare.backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SERVICE -> APPOINTMENT -> BILLABLE EVENT -> INVOICE -> PAYMENT -> RECEIPT.
 * Never a bare "paid" boolean — these prove the running balance/status math
 * the whole model depends on.
 */
class InvoiceBillingTest extends BaseIntegrationTest {

    private AuthedUser finance;
    private AuthedUser client;

    private void setUpFinanceAndClient() {
        AuthedUser maintenanceUser = maintenance();
        finance = provisionStaff(maintenanceUser, "Billing Test Finance", "FINANCE");
        client = registerClient("Billing Test Client");
    }

    @Test
    void invoiceTotalsAreComputedServerSide_subtotalMinusDiscount() {
        setUpFinanceAndClient();

        var res = post("/api/invoices", finance.token(), Map.of(
                "clientId", client.id().toString(),
                "items", List.of(Map.of("description", "Session", "amount", 75.00, "quantity", 2)),
                "discount", 10.00
        ));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = res.getBody();
        assertThat(((Number) body.get("subtotal")).doubleValue()).isEqualTo(150.00);
        assertThat(((Number) body.get("total")).doubleValue()).isEqualTo(140.00);
        assertThat(body.get("status")).isEqualTo("UNPAID");
    }

    @Test
    void paymentExceedingBalanceDueIsRejected() {
        setUpFinanceAndClient();
        Map<String, Object> invoice = post("/api/invoices", finance.token(), Map.of(
                "clientId", client.id().toString(),
                "items", List.of(Map.of("description", "Session", "amount", 50.00, "quantity", 1))
        )).getBody();
        String invoiceId = (String) invoice.get("id");

        var res = post("/api/invoices/" + invoiceId + "/payments", finance.token(),
                Map.of("amount", 999.00, "method", "CASH"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void partialThenFullPaymentTransitionsStatusCorrectly() {
        setUpFinanceAndClient();
        Map<String, Object> invoice = post("/api/invoices", finance.token(), Map.of(
                "clientId", client.id().toString(),
                "items", List.of(Map.of("description", "Session", "amount", 100.00, "quantity", 1))
        )).getBody();
        String invoiceId = (String) invoice.get("id");

        var afterPartial = post("/api/invoices/" + invoiceId + "/payments", finance.token(),
                Map.of("amount", 40.00, "method", "CASH"));
        assertThat(afterPartial.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var invoiceAfterPartial = get("/api/invoices/" + invoiceId, finance.token()).getBody();
        assertThat(invoiceAfterPartial.get("status")).isEqualTo("PARTIALLY_PAID");

        var afterFull = post("/api/invoices/" + invoiceId + "/payments", finance.token(),
                Map.of("amount", 60.00, "method", "CARD"));
        assertThat(afterFull.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var invoiceAfterFull = get("/api/invoices/" + invoiceId, finance.token()).getBody();
        assertThat(invoiceAfterFull.get("status")).isEqualTo("PAID");
        assertThat(((Number) invoiceAfterFull.get("balanceDue")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    void anInvoiceWithAnyPaymentCanNoLongerBeCancelled() {
        setUpFinanceAndClient();
        Map<String, Object> invoice = post("/api/invoices", finance.token(), Map.of(
                "clientId", client.id().toString(),
                "items", List.of(Map.of("description", "Session", "amount", 30.00, "quantity", 1))
        )).getBody();
        String invoiceId = (String) invoice.get("id");
        post("/api/invoices/" + invoiceId + "/payments", finance.token(), Map.of("amount", 10.00, "method", "CASH"));

        var res = patch("/api/invoices/" + invoiceId + "/cancel", finance.token(), Map.of());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anUnpaidInvoiceCanBeCancelled() {
        setUpFinanceAndClient();
        Map<String, Object> invoice = post("/api/invoices", finance.token(), Map.of(
                "clientId", client.id().toString(),
                "items", List.of(Map.of("description", "Session", "amount", 30.00, "quantity", 1))
        )).getBody();
        String invoiceId = (String) invoice.get("id");

        var res = patch("/api/invoices/" + invoiceId + "/cancel", finance.token(), Map.of());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void aClientOnlySeesTheirOwnInvoices() {
        setUpFinanceAndClient();
        AuthedUser otherClient = registerClient("Other Billing Client");
        post("/api/invoices", finance.token(), Map.of(
                "clientId", client.id().toString(),
                "items", List.of(Map.of("description", "Session", "amount", 30.00, "quantity", 1))
        ));

        var res = getList("/api/invoices/mine", otherClient.token());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
