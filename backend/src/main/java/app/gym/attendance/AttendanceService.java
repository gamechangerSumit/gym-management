
package app.gym.attendance;

import app.gym.attendance.AttendanceDtos.AttendanceDto;
import app.gym.attendance.AttendanceDtos.ScanResponse;
import app.gym.auth.CurrentUser;
import app.gym.common.ApiException;
import app.gym.common.Db;
import app.gym.common.PageResponse;
import app.gym.common.Paging;
import app.gym.member.MemberDtos.MemberBasic;
import app.gym.member.MemberService;
import app.gym.qr.QrDtos;
import app.gym.qr.QrPassService;
import app.gym.qr.QrPassService.PassRef;
import app.gym.settings.GymClock;
import app.gym.settings.GymSettingsService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {

    private record MembershipState(
            boolean valid,
            boolean held,
            boolean past,
            LocalDate validEnd
    ) {}

    private final JdbcClient jdbc;
    private final GymClock clock;
    private final GymSettingsService settings;
    private final QrPassService passes;
    private final MemberService members;
    private final NoShowService noShows;

    @Autowired
    public AttendanceService(
            JdbcClient jdbc,
            GymClock clock,
            GymSettingsService settings,
            QrPassService passes,
            MemberService members,
            NoShowService noShows
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.passes = passes;
        this.members = members;
        this.noShows = noShows;
    }

    public AttendanceService(JdbcClient jdbc, GymClock clock, GymSettingsService settings,
                             QrPassService passes, MemberService members) {
        this(jdbc, clock, settings, passes, members, null);
    }

    /**
     * Validation order:
     * authenticated staff/owner (security layer)
     * -> token hash found
     * -> pass ACTIVE
     * -> member ACTIVE
     * -> membership valid today
     * -> not a duplicate within the configured window.
     *
     * Only CHECKED_IN writes an attendance record.
     */
    @Transactional
    public ScanResponse scan(String rawToken) {
        String token = rawToken.trim();

        if (token.startsWith(QrDtos.PREFIX)) {
            token = token.substring(QrDtos.PREFIX.length());
        }

        if (token.isEmpty() || token.length() > 100) {
            return reject(
                    ScanResult.INVALID_TOKEN,
                    "This QR code is not a valid gym pass."
            );
        }

        Optional<PassRef> found = passes.findByToken(token);

        if (found.isEmpty()) {
            return reject(
                    ScanResult.INVALID_TOKEN,
                    "This QR code is not recognised. It may be old or not from this gym."
            );
        }

        PassRef pass = found.get();

        if (!"ACTIVE".equals(pass.status())) {
            return reject(
                    ScanResult.PASS_REVOKED,
                    "This pass was replaced or revoked. Ask the member for their current pass."
            );
        }

        return checkIn(pass.memberId(), "QR", pass.id());
    }

    @Transactional
    public ScanResponse manual(long memberId, String reason) {
        members.basic(memberId);
        if (reason == null || reason.isBlank() || reason.length() > 80) {
            throw ApiException.badRequest("INVALID_ASSISTED_REASON", "Choose a reason for assisted check-in.");
        }
        return checkIn(memberId, "MANUAL", null, reason.trim());
    }

    private ScanResponse checkIn(long memberId, String source, Long passId) {
        return checkIn(memberId, source, passId, null);
    }

    private ScanResponse checkIn(long memberId, String source, Long passId, String assistedReason) {

        // Lock the member row so concurrent scans for the same member
        // cannot both pass the duplicate check.
        jdbc.sql(
                        "SELECT id FROM member_profile WHERE id = :id FOR UPDATE"
                )
                .param("id", memberId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Member not found."));

        MemberBasic member = members.basic(memberId);

        if (!"ACTIVE".equals(member.status())) {
            return withMember(
                    ScanResult.MEMBER_INACTIVE,
                    "This member is inactive.",
                    member,
                    null,
                    null
            );
        }

        LocalDate today = clock.today();
        MembershipState state = membershipState(memberId, today);

        if (!state.valid()) {
            if (state.held()) {
                return withMember(
                        ScanResult.ON_HOLD,
                        "Membership is on hold.",
                        member,
                        null,
                        null
                );
            }

            if (state.past()) {
                return withMember(
                        ScanResult.EXPIRED,
                        "Membership has expired.",
                        member,
                        null,
                        null
                );
            }

            return withMember(
                    ScanResult.NO_ACTIVE_MEMBERSHIP,
                    "No active membership for today.",
                    member,
                    null,
                    null
            );
        }

        Instant now = clock.now();
        int windowMinutes = settings.get().duplicateWindowMinutes();

        if (windowMinutes > 0) {
            /*
             * FIX:
             * MAX(checked_in_at) returns NULL when the member has
             * no attendance records. Query the latest existing row
             * instead, allowing an empty result safely.
             */
            Instant last = jdbc.sql(
                            "SELECT checked_in_at AS last " +
                            "FROM attendance_record " +
                            "WHERE member_id = :m " +
                            "ORDER BY checked_in_at DESC " +
                            "LIMIT 1"
                    )
                    .param("m", memberId)
                    .query((rs, i) -> Db.instant(rs, "last"))
                    .optional()
                    .orElse(null);

            if (last != null
                    && Duration.between(last, now)
                    .compareTo(Duration.ofMinutes(windowMinutes)) < 0) {

                return withMember(
                        ScanResult.DUPLICATE_RECENT,
                        "Already checked in a moment ago.",
                        member,
                        state.validEnd(),
                        last
                );
            }
        }

        jdbc.sql(
                        "INSERT INTO attendance_record " +
                        "(member_id, checked_in_at, checkin_date, source, recorded_by, qr_pass_id, assisted_reason) " +
                        "VALUES (:m, :at, :d, :src, :by, :pass, :reason)"
                )
                .param("m", memberId)
                .param("at", Db.ts(now))
                .param("d", today)
                .param("src", source)
                .param("by", CurrentUser.idOrNull())
                .param("pass", passId)
                .param("reason", assistedReason)
                .update();

        // A valid return visit closes the open risk case while preserving its history.
        if (noShows != null) noShows.resolveOnReturn(memberId);

        return withMember(
                ScanResult.CHECKED_IN,
                "Checked in.",
                member,
                state.validEnd(),
                now
        );
    }

    private MembershipState membershipState(
            long memberId,
            LocalDate today
    ) {
        return jdbc.sql(
                        "SELECT " +
                        "bool_or(status = 'ACTIVE' AND start_date <= :t AND end_date >= :t) AS valid, " +
                        "bool_or(status = 'ON_HOLD' AND end_date >= :t) AS held, " +
                        "bool_or(end_date < :t) AS past, " +
                        "max(end_date) FILTER (" +
                        "WHERE status = 'ACTIVE' AND start_date <= :t AND end_date >= :t" +
                        ") AS valid_end " +
                        "FROM membership " +
                        "WHERE member_id = :m AND status <> 'CANCELLED'"
                )
                .param("t", today)
                .param("m", memberId)
                .query((rs, i) -> new MembershipState(
                        rs.getBoolean("valid"),
                        rs.getBoolean("held"),
                        rs.getBoolean("past"),
                        Db.date(rs, "valid_end")
                ))
                .single();
    }

    private static ScanResponse reject(
            ScanResult result,
            String message
    ) {
        return new ScanResponse(
                result,
                message,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static ScanResponse withMember(
            ScanResult result,
            String message,
            MemberBasic member,
            LocalDate end,
            Instant at
    ) {
        return new ScanResponse(
                result,
                message,
                member.id(),
                member.memberCode(),
                member.fullName(),
                end,
                at
        );
    }

    public PageResponse<AttendanceDto> list(
            LocalDate from,
            LocalDate to,
            Long memberId,
            Paging paging
    ) {
        LocalDate today = clock.today();
        LocalDate f = from != null ? from : today;
        LocalDate t = to != null ? to : f;

        StringBuilder where = new StringBuilder(
                " WHERE a.checkin_date BETWEEN :f AND :t"
        );

        Map<String, Object> params = new HashMap<>();
        params.put("f", f);
        params.put("t", t);

        if (memberId != null) {
            where.append(" AND a.member_id = :m");
            params.put("m", memberId);
        }

        long total = jdbc.sql(
                        "SELECT count(*) FROM attendance_record a" + where
                )
                .params(params)
                .query(Long.class)
                .single();

        Map<String, Object> lp = new HashMap<>(params);
        lp.put("lim", paging.size());
        lp.put("off", paging.offset());

        List<AttendanceDto> items = jdbc.sql(
                        "SELECT a.id, a.member_id, m.member_code, m.full_name, " +
                        "a.checked_in_at, a.checkin_date, a.source, u.login_id, a.assisted_reason " +
                        "FROM attendance_record a " +
                        "JOIN member_profile m ON m.id = a.member_id " +
                        "LEFT JOIN user_account u ON u.id = a.recorded_by" +
                        where +
                        " ORDER BY a.checked_in_at DESC, a.id DESC " +
                        "LIMIT :lim OFFSET :off"
                )
                .params(lp)
                .query((rs, i) -> new AttendanceDto(
                        rs.getLong("id"),
                        rs.getLong("member_id"),
                        rs.getString("member_code"),
                        rs.getString("full_name"),
                        Db.instant(rs, "checked_in_at"),
                        Db.date(rs, "checkin_date"),
                        rs.getString("source"),
                        rs.getString("login_id"),
                        rs.getString("assisted_reason")
                ))
                .list();

        return PageResponse.of(
                items,
                paging.page(),
                paging.size(),
                total
        );
    }
}
