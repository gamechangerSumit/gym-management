package app.gym.member;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Sequence-backed, human-friendly member codes: M00001, M00002, ... */
@Component
public class MemberCodeGenerator {

    private final JdbcClient jdbc;

    public MemberCodeGenerator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String next() {
        long n = jdbc.sql("SELECT nextval('member_code_seq')").query(Long.class).single();
        return String.format("M%05d", n);
    }
}
