package org.rapla.rest.dto;

/**
 * Wire shape for a recents/favorites entry (PRD 089). Mirrors the SPA's
 * {@code ResourceItem}: presentation fields ({@code label}, {@code color},
 * {@code typeKey}) are resolved live from the entity at read time (D3), never
 * stored — so labels stay fresh and a deleted/now-invisible entity simply drops
 * out of the read.
 *
 * @param id      opaque entity id (allocatable or user)
 * @param kind    {@code resource} or {@code user}
 * @param label   display name resolved at read time
 * @param color   hex colour for a resource, or {@code null}
 * @param typeKey DynamicType key for a resource, or {@code null} for a user
 */
public record UserListItem(String id, String kind, String label, String color, String typeKey)
{
}
