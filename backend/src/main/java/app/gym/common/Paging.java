package app.gym.common;

/** Clamped page request. Pages are zero-based. */
public record Paging(int page, int size) {
    public static final int MAX_SIZE = 100;

    public static Paging of(Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.min(MAX_SIZE, Math.max(1, size));
        return new Paging(p, s);
    }

    public int offset() {
        return page * size;
    }
}
