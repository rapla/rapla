package org.rapla.plugin.tableview;

/**
 * Pagination request shape for {@link TableViewEngine}. Three modes:
 *
 * <ul>
 *   <li>{@link #ALL} — return every matching row, no cap. Used by the
 *       Swing-style "everything in one scroll" UX and by callers who
 *       know the result is small.</li>
 *   <li>{@link #firstPage(int)} — return at most {@code pageSize} rows
 *       from the start; emit a {@code nextCursor} if more rows remain.</li>
 *   <li>{@link #nextPage(int, String)} — return at most {@code pageSize}
 *       rows starting <em>after</em> the row identified by {@code cursor}.</li>
 *   <li>{@link #allWithCap(int)} — return every matching row, but cap at
 *       {@code maxRows}; if the cap is hit, set {@code incomplete=true}
 *       on the response and emit a cursor so the client can keep
 *       loading. Used by the server to bound the "no pageSize" worst case.</li>
 * </ul>
 *
 * @param pageSize {@code null} means "no limit" (subject to {@link #cap}).
 * @param cursor   opaque token from a prior page's {@code nextCursor};
 *                 {@code null} means "start from the beginning".
 * @param cap      server-side maximum number of rows to return when
 *                 {@code pageSize} is null. {@code 0} = no cap.
 */
public record PageSpec(Integer pageSize, String cursor, int cap)
{
    public static final PageSpec ALL = new PageSpec(null, null, 0);

    public static PageSpec firstPage(int pageSize)
    {
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be > 0");
        return new PageSpec(pageSize, null, 0);
    }

    public static PageSpec nextPage(int pageSize, String cursor)
    {
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize must be > 0");
        if (cursor == null) throw new IllegalArgumentException("cursor must not be null");
        return new PageSpec(pageSize, cursor, 0);
    }

    public static PageSpec allWithCap(int maxRows)
    {
        if (maxRows < 0) throw new IllegalArgumentException("maxRows must be >= 0");
        return new PageSpec(null, null, maxRows);
    }
}
