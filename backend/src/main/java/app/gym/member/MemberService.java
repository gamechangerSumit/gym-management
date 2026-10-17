package app.gym.member;

import app.gym.audit.AuditService;
import app.gym.auth.UserAccountRepository;
import app.gym.common.ApiException;
import app.gym.common.Db;
import app.gym.common.PageResponse;
import app.gym.common.Paging;
import app.gym.config.AppProperties;
import app.gym.member.MemberDtos.CreateMemberRequest;
import app.gym.member.MemberDtos.MemberBasic;
import app.gym.member.MemberDtos.MemberDetail;
import app.gym.member.MemberDtos.MemberSummary;
import app.gym.member.MemberDtos.UpdateMemberRequest;
import app.gym.membership.StatusCalculator;
import app.gym.qr.QrPassService;
import app.gym.settings.GymClock;
import app.gym.settings.GymSettingsService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    /** Current-or-latest non-cancelled membership per member (valid-today one first, otherwise latest end date). */
    static final String CURRENT_MEMBERSHIP_LATERAL =
            "LEFT JOIN LATERAL (SELECT ms.status AS stored_status, ms.start_date, ms.end_date, p.name AS plan_name "
                    + "FROM membership ms JOIN membership_plan p ON p.id = ms.plan_id "
                    + "WHERE ms.member_id = m.id AND ms.status <> 'CANCELLED' "
                    + "ORDER BY (ms.start_date <= :today AND ms.end_date >= :today) DESC, ms.end_date DESC, ms.id DESC "
                    + "LIMIT 1) cur ON true ";

    private final JdbcClient jdbc;
    private final GymClock clock;
    private final GymSettingsService settings;
    private final AuditService audit;
    private final AppProperties props;
    private final MemberCodeGenerator codes;
    private final UserAccountRepository users;
    private final QrPassService qr;

    public MemberService(JdbcClient jdbc, GymClock clock, GymSettingsService settings, AuditService audit,
                         AppProperties props, MemberCodeGenerator codes, UserAccountRepository users, QrPassService qr) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.audit = audit;
        this.props = props;
        this.codes = codes;
        this.users = users;
        this.qr = qr;
    }

    // ---------------------------------------------------------------- queries

    public PageResponse<MemberSummary> search(String q, String status, Paging paging) {
        LocalDate today = clock.today();
        int soon = settings.get().expiringSoonDays();
        StringBuilder where = new StringBuilder(" WHERE m.gym_id = 1");
        Map<String, Object> params = new HashMap<>();
        params.put("today", today);
        if (status != null && !status.isBlank()) {
            if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
                throw ApiException.badRequest("INVALID_STATUS", "Status must be ACTIVE or INACTIVE.");
            }
            where.append(" AND m.status = :status");
            params.put("status", status);
        }
        if (q != null && !q.isBlank()) {
            String term = q.trim();
            where.append(" AND (m.full_name ILIKE :like OR m.member_code ILIKE :like");
            params.put("like", "%" + Db.escapeLike(term) + "%");
            String digits = term.replaceAll("\\D", "");
            if (digits.length() >= 3) {
                where.append(" OR m.phone LIKE :phoneLike");
                params.put("phoneLike", "%" + digits + "%");
            }
            where.append(")");
        }

        long total = jdbc.sql("SELECT count(*) FROM member_profile m" + where)
                .params(params).query(Long.class).single();

        Map<String, Object> listParams = new HashMap<>(params);
        listParams.put("lim", paging.size());
        listParams.put("off", paging.offset());
        List<MemberSummary> items = jdbc.sql("SELECT m.id, m.member_code, m.full_name, m.phone, m.status, "
                        + "cur.stored_status, cur.start_date, cur.end_date, cur.plan_name FROM member_profile m "
                        + CURRENT_MEMBERSHIP_LATERAL + where
                        + " ORDER BY lower(m.full_name), m.id LIMIT :lim OFFSET :off")
                .params(listParams)
                .query((rs, i) -> {
                    LocalDate end = Db.date(rs, "end_date");
                    String ms = end == null ? null : StatusCalculator.compute(rs.getString("stored_status"),
                            Db.date(rs, "start_date"), end, today, soon);
                    return new MemberSummary(rs.getLong("id"), rs.getString("member_code"), rs.getString("full_name"),
                            rs.getString("phone"), rs.getString("status"), ms, end, rs.getString("plan_name"));
                })
                .list();
        return PageResponse.of(items, paging.page(), paging.size(), total);
    }

    public MemberDetail detail(long id, boolean includeDues) {
        LocalDate today = clock.today();
        int soon = settings.get().expiringSoonDays();
        return jdbc.sql("SELECT m.id, m.member_code, m.full_name, m.phone, m.email, m.join_date, m.emergency_contact, "
                        + "m.status, m.staff_notes, m.user_id, u.status AS account_status, u.login_id, "
                        + "cur.stored_status, cur.start_date, cur.end_date, cur.plan_name, d.dues_paise "
                        + "FROM member_profile m LEFT JOIN user_account u ON u.id = m.user_id "
                        + CURRENT_MEMBERSHIP_LATERAL
                        + "LEFT JOIN member_dues_v d ON d.member_id = m.id WHERE m.id = :id")
                .param("today", today)
                .param("id", id)
                .query((rs, i) -> {
                    LocalDate end = Db.date(rs, "end_date");
                    LocalDate start = Db.date(rs, "start_date");
                    String ms = end == null ? null
                            : StatusCalculator.compute(rs.getString("stored_status"), start, end, today, soon);
                    return new MemberDetail(rs.getLong("id"), rs.getString("member_code"), rs.getString("full_name"),
                            rs.getString("phone"), rs.getString("email"), Db.date(rs, "join_date"),
                            rs.getString("emergency_contact"), rs.getString("status"), rs.getString("staff_notes"),
                            Db.longOrNull(rs, "user_id") != null, rs.getString("account_status"),
                            rs.getString("login_id"), ms, start, end, rs.getString("plan_name"),
                            includeDues ? rs.getLong("dues_paise") : null);
                })
                .optional()
                .orElseThrow(() -> ApiException.notFound("Member not found."));
    }

    public MemberBasic basic(long id) {
        return jdbc.sql("SELECT id, member_code, full_name, status, user_id FROM member_profile WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> new MemberBasic(rs.getLong("id"), rs.getString("member_code"),
                        rs.getString("full_name"), rs.getString("status"), Db.longOrNull(rs, "user_id")))
                .optional()
                .orElseThrow(() -> ApiException.notFound("Member not found."));
    }

    // --------------------------------------------------------------- commands

    @Transactional
    public MemberDetail create(CreateMemberRequest req) {
        String name = normalizeName(req.fullName());
        String phone = PhoneNormalizer.normalize(req.phone(), props.defaultCountryCode());
        rejectDuplicatePhone(phone, null);
        if (!Boolean.TRUE.equals(req.confirmSimilar())) {
            rejectSimilar(name, phone, null);
        }
        LocalDate joined = req.joinDate() != null ? req.joinDate() : clock.today();
        long id;
        try {
            id = jdbc.sql("INSERT INTO member_profile (member_code, full_name, phone, email, join_date, "
                            + "emergency_contact, staff_notes) VALUES (:code, :name, :phone, :email, :joined, :ec, :notes) "
                            + "RETURNING id")
                    .param("code", codes.next())
                    .param("name", name)
                    .param("phone", phone)
                    .param("email", blankToNull(req.email()))
                    .param("joined", joined)
                    .param("ec", blankToNull(req.emergencyContact()))
                    .param("notes", blankToNull(req.staffNotes()))
                    .query(Long.class).single();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("DUPLICATE_PHONE", "A member with this phone number already exists.");
        }
        qr.ensureActivePass(id);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fullName", name);
        audit.record("MEMBER_CREATED", "MEMBER", id, details);
        return detail(id, true);
    }

    @Transactional
    public MemberDetail update(long id, UpdateMemberRequest req) {
        MemberDtos.MemberDetail before = detail(id, false);
        String name = normalizeName(req.fullName());
        String phone = PhoneNormalizer.normalize(req.phone(), props.defaultCountryCode());
        boolean nameOrPhoneChanged = !name.equals(before.fullName()) || !phone.equals(before.phone());
        if (!phone.equals(before.phone())) {
            rejectDuplicatePhone(phone, id);
        }
        if (nameOrPhoneChanged && !Boolean.TRUE.equals(req.confirmSimilar())) {
            rejectSimilar(name, phone, id);
        }
        LocalDate joined = req.joinDate() != null ? req.joinDate() : before.joinDate();
        try {
            jdbc.sql("UPDATE member_profile SET full_name = :name, phone = :phone, email = :email, join_date = :joined, "
                            + "emergency_contact = :ec, staff_notes = :notes, updated_at = now() WHERE id = :id")
                    .param("name", name)
                    .param("phone", phone)
                    .param("email", blankToNull(req.email()))
                    .param("joined", joined)
                    .param("ec", blankToNull(req.emergencyContact()))
                    .param("notes", blankToNull(req.staffNotes()))
                    .param("id", id)
                    .update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("DUPLICATE_PHONE", "A member with this phone number already exists.");
        }
        List<String> changed = new ArrayList<>();
        if (!name.equals(before.fullName())) changed.add("fullName");
        if (!phone.equals(before.phone())) changed.add("phone");
        if (!java.util.Objects.equals(blankToNull(req.email()), before.email())) changed.add("email");
        if (!java.util.Objects.equals(joined, before.joinDate())) changed.add("joinDate");
        if (!java.util.Objects.equals(blankToNull(req.emergencyContact()), before.emergencyContact())) changed.add("emergencyContact");
        if (!java.util.Objects.equals(blankToNull(req.staffNotes()), before.staffNotes())) changed.add("staffNotes");
        audit.record("MEMBER_UPDATED", "MEMBER", id, Map.of("changedFields", changed));
        return detail(id, true);
    }

    @Transactional
    public MemberDetail deactivate(long id, String reason) {
        MemberBasic member = basic(id);
        if ("INACTIVE".equals(member.status())) {
            return detail(id, true);
        }
        jdbc.sql("UPDATE member_profile SET status = 'INACTIVE', updated_at = now() WHERE id = :id").param("id", id).update();
        if (member.userId() != null) {
            users.findById(member.userId()).ifPresent(u -> {
                if ("ACTIVE".equals(u.status())) {
                    users.setStatus(u.id(), "DISABLED");
                    users.endSessions(u.loginId());
                }
            });
        }
        audit.record("MEMBER_DEACTIVATED", "MEMBER", id, Map.of("reason", reason == null ? "" : reason));
        return detail(id, true);
    }

    @Transactional
    public MemberDetail reactivate(long id) {
        MemberBasic member = basic(id);
        if ("ACTIVE".equals(member.status())) {
            return detail(id, true);
        }
        jdbc.sql("UPDATE member_profile SET status = 'ACTIVE', updated_at = now() WHERE id = :id").param("id", id).update();
        if (member.userId() != null) {
            users.findById(member.userId()).ifPresent(u -> {
                if ("DISABLED".equals(u.status()) && u.passwordHash() != null) {
                    users.setStatus(u.id(), "ACTIVE");
                }
            });
        }
        audit.record("MEMBER_REACTIVATED", "MEMBER", id, Map.of());
        return detail(id, true);
    }

    // ---------------------------------------------------------------- helpers

    private void rejectDuplicatePhone(String phone, Long selfId) {
        Map<String, Object> params = new HashMap<>();
        params.put("p", phone);
        if (selfId != null) {
            params.put("self", selfId);
        }
        Optional<MemberBasic> existing = jdbc.sql("SELECT id, member_code, full_name, status, user_id FROM member_profile "
                        + "WHERE phone = :p" + (selfId == null ? "" : " AND id <> :self"))
                .params(params)
                .query((rs, i) -> new MemberBasic(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), null))
                .optional();
        if (existing.isPresent()) {
            throw ApiException.conflict("DUPLICATE_PHONE", "This phone number already belongs to member "
                    + existing.get().memberCode() + " (" + existing.get().fullName() + ").");
        }
    }

    /** Warns (409 SIMILAR_MEMBER) when a very similar name has a phone differing by at most two digits. */
    private void rejectSimilar(String name, String phone, Long selfId) {
        List<MemberBasic> candidates = new ArrayList<>();
        List<String> phones = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("n", name);
        if (selfId != null) {
            params.put("self", selfId);
        }
        jdbc.sql("SELECT id, member_code, full_name, status, phone FROM member_profile "
                        + "WHERE similarity(lower(full_name), lower(:n)) >= 0.8"
                        + (selfId == null ? "" : " AND id <> :self") + " LIMIT 20")
                .params(params)
                .query((rs, i) -> {
                    candidates.add(new MemberBasic(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), null));
                    phones.add(rs.getString(5));
                    return 0;
                }).list();
        for (int i = 0; i < candidates.size(); i++) {
            if (hamming(phones.get(i), phone) <= 2) {
                MemberBasic c = candidates.get(i);
                throw ApiException.conflict("SIMILAR_MEMBER", "A very similar member already exists: " + c.memberCode()
                        + " " + c.fullName() + " (" + phones.get(i) + "). Save again to confirm this is a different person.");
            }
        }
    }

    static int hamming(String a, String b) {
        if (a.length() != b.length()) {
            return Integer.MAX_VALUE;
        }
        int d = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                d++;
            }
        }
        return d;
    }

    static String normalizeName(String raw) {
        return raw.trim().replaceAll("\\s+", " ");
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
