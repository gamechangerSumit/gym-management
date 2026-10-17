package app.gym.report;

import java.util.List;

/** Minimal CSV writer. Cells starting with = + - @ are prefixed with ' so spreadsheets do not run them as formulas. */
public final class Csv {
    private Csv() {}

    public static String line(List<?> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(cells.get(i)));
        }
        return sb.append("\r\n").toString();
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        boolean numeric = value instanceof Number;
        if (!numeric && !s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
