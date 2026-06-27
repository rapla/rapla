package org.rapla.server.spring.graphql;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 089 Phase 3 (D4) — tier-1 unit test for the in-bucket re-rank: within a
 * kind bucket a favorite hit sorts above an equally-ranked recent, which sorts
 * above an equally-ranked plain hit, and the boosted {@code score} reflects that
 * order (higher = better on the wire).
 */
class SearchRankBoostTest
{
    @Test
    void favoriteOutranksEquallyRankedPlain()
    {
        Set<String> favorites = Set.of("fav");
        Set<String> recents = Set.of();

        SearchRankBoost.Tier favTier = SearchRankBoost.tierOf("fav", favorites, recents);
        SearchRankBoost.Tier plainTier = SearchRankBoost.tierOf("plain", favorites, recents);

        assertEquals(SearchRankBoost.Tier.FAVORITE, favTier);
        assertEquals(SearchRankBoost.Tier.PLAIN, plainTier);

        int sameRank = 5;
        double favScore = SearchRankBoost.boostedScore(SearchGraphQLController.score(sameRank), favTier);
        double plainScore = SearchRankBoost.boostedScore(SearchGraphQLController.score(sameRank), plainTier);

        assertTrue(favScore > plainScore,
                () -> "favorite must outrank an equally-ranked plain hit: " + favScore + " !> " + plainScore);
    }

    @Test
    void recentOutranksEquallyRankedPlain()
    {
        Set<String> favorites = Set.of();
        Set<String> recents = Set.of("rec");

        SearchRankBoost.Tier recTier = SearchRankBoost.tierOf("rec", favorites, recents);
        SearchRankBoost.Tier plainTier = SearchRankBoost.tierOf("plain", favorites, recents);

        assertEquals(SearchRankBoost.Tier.RECENT, recTier);
        assertEquals(SearchRankBoost.Tier.PLAIN, plainTier);

        int sameRank = 5;
        double recScore = SearchRankBoost.boostedScore(SearchGraphQLController.score(sameRank), recTier);
        double plainScore = SearchRankBoost.boostedScore(SearchGraphQLController.score(sameRank), plainTier);

        assertTrue(recScore > plainScore,
                () -> "recent must outrank an equally-ranked plain hit: " + recScore + " !> " + plainScore);
    }

    @Test
    void favoriteOutranksRecentAndFavoriteWins()
    {
        Set<String> favorites = Set.of("both");
        Set<String> recents = Set.of("both", "rec");

        // a hit that is both a favorite and a recent is treated as a favorite
        assertEquals(SearchRankBoost.Tier.FAVORITE,
                SearchRankBoost.tierOf("both", favorites, recents));

        int sameRank = 5;
        double favScore = SearchRankBoost.boostedScore(SearchGraphQLController.score(sameRank),
                SearchRankBoost.Tier.FAVORITE);
        double recScore = SearchRankBoost.boostedScore(SearchGraphQLController.score(sameRank),
                SearchRankBoost.Tier.RECENT);

        assertTrue(favScore > recScore,
                () -> "favorite must outrank an equally-ranked recent: " + favScore + " !> " + recScore);
    }

    @Test
    void aBetterRankedPlainStillLosesToFavorite()
    {
        // base score is bounded in (0,1]; the tier offset (>=1) dominates, so a
        // favorite with the WORST possible rank still beats the best plain hit.
        double bestPlain = SearchRankBoost.boostedScore(SearchGraphQLController.score(0),
                SearchRankBoost.Tier.PLAIN);
        double worstFavorite = SearchRankBoost.boostedScore(SearchGraphQLController.score(Integer.MAX_VALUE - 1),
                SearchRankBoost.Tier.FAVORITE);
        assertTrue(worstFavorite > bestPlain,
                () -> "tier must dominate base rank: worstFav=" + worstFavorite + " bestPlain=" + bestPlain);
    }
}
