package org.rapla.server.spring.document;

import java.time.LocalDate;
import java.util.Optional;
import org.rapla.server.spring.graphql.ViewAnchor;
import org.rapla.server.spring.graphql.ViewDateUnit;
import org.rapla.server.spring.graphql.WindowResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * PRD 097 — the DOCUMENT-level window (decided 2026-07-15): anchors are presentation POLICY and
 * live on the document ("this Aushang shows the current week"); the view's {@code @window} is
 * demoted to a DEFAULT (fresh-document preview, GraphiQL, SPA seed). Precedence, highest first:
 * URL {@code ?from/?to} → absolute dates in {@code defaultVariables} → THIS → view {@code @window}
 * → render-mode default.
 *
 * <p>Stored as metadata JSON in the same shape as the directive:
 * {@code {"from":{"anchor":"MONTH_START"},"to":{"anchor":"MONTH_START","offset":1,"unit":"MONTHS"}}}
 * — {@code offset} defaults 0, {@code unit} defaults DAYS, exactly like {@code WindowAnchor}.
 */
final class DocumentWindow
{
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    record AnchorSpec(String anchor, Integer offset, String unit) { }

    record WindowSpec(AnchorSpec from, AnchorSpec to) { }

    private DocumentWindow() {}

    /** Parse + resolve against {@code today}; empty when absent, unparseable, or incomplete. */
    static Optional<WindowResolver.Window> resolve(String json, LocalDate today)
    {
        WindowSpec spec = parse(json);
        if (spec == null || spec.from() == null || spec.to() == null) return Optional.empty();
        try
        {
            return Optional.of(new WindowResolver.Window(
                    resolveAnchor(spec.from(), today), resolveAnchor(spec.to(), today)));
        }
        catch (IllegalArgumentException e)
        {
            return Optional.empty();   // unknown anchor/unit name — surfaced by validate() at save
        }
    }

    /** Save-gate check: null = fine (absent or valid), otherwise the human-readable defect. */
    static String validate(String json)
    {
        if (json == null || json.isBlank()) return null;
        WindowSpec spec = parse(json);
        if (spec == null) return "window is not a JSON object of shape {from:{anchor,offset,unit}, to:{...}}";
        if (spec.from() == null || spec.to() == null) return "window needs both from and to anchors";
        for (AnchorSpec anchorSpec : new AnchorSpec[] { spec.from(), spec.to() })
        {
            try
            {
                resolveAnchor(anchorSpec, LocalDate.now());
            }
            catch (IllegalArgumentException e)
            {
                return "window anchor invalid: " + e.getMessage();
            }
        }
        return null;
    }

    private static String resolveAnchor(AnchorSpec spec, LocalDate today)
    {
        if (spec.anchor() == null) throw new IllegalArgumentException("anchor is required");
        ViewAnchor anchor = ViewAnchor.valueOf(spec.anchor());
        ViewDateUnit unit = spec.unit() == null ? ViewDateUnit.DAYS : ViewDateUnit.valueOf(spec.unit());
        int offset = spec.offset() == null ? 0 : spec.offset();
        return WindowResolver.resolve(anchor, offset, unit, today);
    }

    private static WindowSpec parse(String json)
    {
        if (json == null || json.isBlank()) return null;
        try
        {
            return MAPPER.readValue(json, WindowSpec.class);
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
