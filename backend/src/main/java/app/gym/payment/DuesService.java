package app.gym.payment;

import app.gym.payment.DuesCalculator.ChargeLine;
import app.gym.payment.DuesCalculator.Dues;
import app.gym.payment.DuesCalculator.PaymentLine;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DuesService {

    private final JdbcClient jdbc;

    public DuesService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Recomputed from the member's membership and payment rows every time. */
    public Dues duesFor(long memberId) {
        List<ChargeLine> charges = jdbc.sql("SELECT price_charged_paise, status FROM membership WHERE member_id = :m")
                .param("m", memberId)
                .query((rs, i) -> new ChargeLine(rs.getLong(1), rs.getString(2)))
                .list();
        List<PaymentLine> payments = jdbc.sql("SELECT amount_paise, status FROM payment WHERE member_id = :m")
                .param("m", memberId)
                .query((rs, i) -> new PaymentLine(rs.getLong(1), rs.getString(2)))
                .list();
        return DuesCalculator.compute(charges, payments);
    }
}
