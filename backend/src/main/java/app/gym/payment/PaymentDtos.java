package app.gym.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record PaymentRequest(
            @NotNull Long memberId,
            Long membershipId,
            @Min(1) @Max(10_000_000_000L) long amountPaise,
            @NotBlank String method,
            Instant paidAt,
            @Size(max = 300) String note,
            Boolean allowOverpayment) {}

    public record VoidRequest(@NotBlank @Size(max = 300) String reason) {}

    public record PaymentDto(long id, long memberId, String memberCode, String memberName, Long membershipId,
                             long amountPaise, Instant paidAt, String method, String receiptNo, String status,
                             String voidReason, Instant voidedAt, String recordedBy, String note) {}

    public record DuesDto(long billedPaise, long paidPaise, long duesPaise) {}

    /** Printable receipt data. Contains no card or bank details: the app never stores them. */
    public record ReceiptDto(String gymName, String receiptNo, String status, String voidReason, Instant paidAt,
                             String paidAtText, long amountPaise, String amountText, String method, String memberCode,
                             String memberName, String planName, String note) {}
}
