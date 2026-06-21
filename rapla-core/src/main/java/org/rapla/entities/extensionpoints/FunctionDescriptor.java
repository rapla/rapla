package org.rapla.entities.extensionpoints;

/**
 * PRD 073 — declarative metadata for one rapla expression-language function, expressed in rapla
 * terms (no GraphQL knowledge). A {@link FunctionFactory} declares its functions as descriptors so a
 * central catalog — the GraphQL {@code computeFunctions} query, editor autocomplete, the future
 * {@code saveView} type-check — can enumerate the allowed functions without parsing source.
 *
 * <ul>
 *   <li>{@code name} / {@code namespace} — the function id and its provider namespace
 *       ({@code org.rapla} for core; the plugin namespace otherwise).</li>
 *   <li>{@code minArgs} / {@code maxArgs} — arity bounds; {@code maxArgs < 0} = unbounded
 *       (variadic, e.g. {@code concat}).</li>
 *   <li>{@code returnType} — the rapla-level result kind ({@code String}, {@code Boolean},
 *       {@code DateTime}, {@code Date}, {@code Int}, {@code TimeInterval}, {@code DynamicType},
 *       {@code AttributeValue}, {@code Entity}, list forms like {@code [Allocatable]}, or the
 *       generic {@code T}/{@code [T]} for collection ops).</li>
 *   <li>{@code sourceLevel} — where the subject comes from: {@code EVENT} (Block/Appointment/
 *       Reservation/CalendarModel), {@code CLASSIFIABLE} (Allocatable/Reservation), {@code
 *       ALLOCATABLE}, {@code ANY} (argument-based, source-agnostic) or {@code VIEW_TITLE}. See
 *       PRD 073 §"Function inventory".</li>
 *   <li>{@code doc} — a one-line human description.</li>
 * </ul>
 */
public record FunctionDescriptor(
        String name,
        String namespace,
        int minArgs,
        int maxArgs,
        String returnType,
        String sourceLevel,
        String doc)
{
}
