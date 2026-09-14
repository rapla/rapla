package org.rapla.plugin.externaleventimport.server;

/** A reservation that might be the one a staged item belongs to. A SUGGESTION — the score
 *  ranks, it never decides; two source records can share a number and be different events. */
public record BindCandidate(String reservationId, String name, String firstDate, double score)
{
}
