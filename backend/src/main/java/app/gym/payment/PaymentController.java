package app.gym.payment;

import app.gym.common.PageResponse;
import app.gym.common.Paging;
import app.gym.payment.PaymentDtos.DuesDto;
import app.gym.payment.PaymentDtos.PaymentDto;
import app.gym.payment.PaymentDtos.PaymentRequest;
import app.gym.payment.PaymentDtos.ReceiptDto;
import app.gym.payment.PaymentDtos.VoidRequest;
import app.gym.payment.DuesCalculator.Dues;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService payments;
    private final ReceiptService receipts;
    private final DuesService dues;

    public PaymentController(PaymentService payments, ReceiptService receipts, DuesService dues) {
        this.payments = payments;
        this.receipts = receipts;
        this.dues = dues;
    }

    @GetMapping("/payments")
    @PreAuthorize("@perm.recordPayments()")
    public PageResponse<PaymentDto> list(@RequestParam(required = false) Long memberId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return payments.search(memberId, from, to, status, Paging.of(page, size));
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.recordPayments()")
    public PaymentDto record(@Valid @RequestBody PaymentRequest req) {
        return payments.record(req);
    }

    @PostMapping("/payments/{id}/void")
    @PreAuthorize("@perm.owner()")
    public PaymentDto voidPayment(@PathVariable long id, @Valid @RequestBody VoidRequest req) {
        return payments.voidPayment(id, req.reason());
    }

    @GetMapping("/payments/{id}/receipt")
    @PreAuthorize("@perm.recordPayments()")
    public ReceiptDto receipt(@PathVariable long id) {
        return receipts.receipt(id);
    }

    @GetMapping(value = "/payments/{id}/receipt.html", produces = MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("@perm.recordPayments()")
    public String receiptHtml(@PathVariable long id) {
        return receipts.html(receipts.receipt(id));
    }

    @GetMapping("/members/{memberId}/dues")
    @PreAuthorize("@perm.recordPayments() or @perm.manageMemberships()")
    public DuesDto duesOf(@PathVariable long memberId) {
        Dues d = dues.duesFor(memberId);
        return new DuesDto(d.billedPaise(), d.paidPaise(), d.duesPaise());
    }
}
