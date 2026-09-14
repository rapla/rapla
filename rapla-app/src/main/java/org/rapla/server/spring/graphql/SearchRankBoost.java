package org.rapla.server.spring.graphql;

import java.util.Set;

/**
 * PRD 089 Phase 3 (D4) — in-bucket re-rank of search matches. Within each kind
 * bucket a hit whose id is in the caller's FAVORITES sorts first, then a hit in
 * the caller's RECENTS, then the existing {@code rank → id} order. This is a
 * re-rank of MATCHES ONLY — no new always-on bucket.
 *
 * <p>The base score from {@link SearchGraphQLController#score(int)} is bounded in
 * {@code (0, 1]}, so a per-tier offset of {@code >= 1} guarantees the tier
 * dominates: any favorite outranks any recent outranks any plain hit on the
 * wire, while ties within a tier keep the original rank order.
 */
final class SearchRankBoost
{
    private SearchRankBoost() {}

    /** Membership tier of a hit id in the caller's lists (FAVORITE wins over RECENT). */
    enum Tier
    {
        FAVORITE(2.0),
        RECENT(1.0),
        PLAIN(0.0);

        final double offset;

        Tier(double offset) { this.offset = offset; }
    }

    static Tier tierOf(String id, Set<String> favoriteIds, Set<String> recentIds)
    {
        if (id != null && favoriteIds != null && favoriteIds.contains(id)) return Tier.FAVORITE;
        if (id != null && recentIds != null && recentIds.contains(id)) return Tier.RECENT;
        return Tier.PLAIN;
    }

    /** Tie-break ordinal for sorting (lower sorts first): FAVORITE=0, RECENT=1, PLAIN=2. */
    static int sortKey(Tier tier)
    {
        return tier.ordinal();
    }

    /** Boost the base score so the wire order matches the tier (higher = better). */
    static double boostedScore(double baseScore, Tier tier)
    {
        return baseScore + tier.offset;
    }
}
