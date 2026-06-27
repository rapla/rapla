package org.rapla.rest.dto;

/**
 * POST body for adding a recent or pinning a favorite (PRD 089). Only the
 * opaque {@code id} and {@code kind} are persisted (D1/D5); presentation is
 * resolved live on read.
 *
 * @param id   opaque entity id (allocatable or user)
 * @param kind {@code resource} or {@code user}
 */
public record UserListEntryRequest(String id, String kind)
{
}
