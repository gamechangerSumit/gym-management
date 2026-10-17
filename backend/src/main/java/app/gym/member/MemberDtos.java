package app.gym.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class MemberDtos {
    private MemberDtos() {}

    public record CreateMemberRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Size(max = 30) String phone,
            @Email @Size(max = 160) String email,
            LocalDate joinDate,
            @Size(max = 200) String emergencyContact,
            @Size(max = 2000) String staffNotes,
            Boolean confirmSimilar) {}

    public record UpdateMemberRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Size(max = 30) String phone,
            @Email @Size(max = 160) String email,
            LocalDate joinDate,
            @Size(max = 200) String emergencyContact,
            @Size(max = 2000) String staffNotes,
            Boolean confirmSimilar) {}

    public record DeactivateRequest(@Size(max = 300) String reason) {}

    public record InviteRequest(@Size(max = 80) String loginId) {}

    /** Row in the member list. membershipStatus is the derived status of the current/latest membership (or null). */
    public record MemberSummary(long id, String memberCode, String fullName, String phone, String status,
                                String membershipStatus, LocalDate membershipEndDate, String planName) {}

    /** Staff/owner view. Never used for the MEMBER role (they get MeDtos.MeProfile, which has no staff notes). */
    public record MemberDetail(long id, String memberCode, String fullName, String phone, String email,
                               LocalDate joinDate, String emergencyContact, String status, String staffNotes,
                               boolean hasAccount, String accountStatus, String accountLoginId,
                               String membershipStatus, LocalDate membershipStartDate, LocalDate membershipEndDate,
                               String planName, Long duesPaise) {}

    /** Minimal identity used by other services. */
    public record MemberBasic(long id, String memberCode, String fullName, String status, Long userId) {}
}
