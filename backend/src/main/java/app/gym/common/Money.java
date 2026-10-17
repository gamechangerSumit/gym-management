package app.gym.common;

/** Money is always integer paise internally; this formats it for humans (INR, Indian digit grouping). */
public final class Money {
    private Money() {}

    /** 123456789 paise -> "₹12,34,567.89" */
    public static String formatInr(long paise) {
        boolean negative = paise < 0;
        long abs = Math.abs(paise);
        long rupees = abs / 100;
        long rem = abs % 100;
        String digits = Long.toString(rupees);
        String grouped;
        if (digits.length() <= 3) {
            grouped = digits;
        } else {
            String last3 = digits.substring(digits.length() - 3);
            String rest = digits.substring(0, digits.length() - 3);
            StringBuilder sb = new StringBuilder();
            int first = rest.length() % 2;
            if (first > 0) {
                sb.append(rest, 0, first);
            }
            for (int i = first; i < rest.length(); i += 2) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(rest, i, i + 2);
            }
            grouped = sb + "," + last3;
        }
        return (negative ? "-" : "") + "₹" + grouped + "." + String.format("%02d", rem);
    }
}
