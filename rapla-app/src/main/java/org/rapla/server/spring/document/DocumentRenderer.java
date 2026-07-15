package org.rapla.server.spring.document;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import com.samskivert.mustache.Template;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * PRD 097 D2 — the JMustache engine wrapper. Logic-less by construction: there is no
 * expression language, so a template-injection payload is just a missing key and renders
 * empty (the structural no-SSTI guarantee). {@code {{ }}} auto-escapes interpolated query
 * data into HTML; {@code {{{ }}}} is never used for query-derived values (D6).
 *
 * <p>Compiled templates are memoized by body content hash — a pure derivation, so the cache
 * is self-invalidating when the body changes (PRD 098 "caching stance", §16-safe).
 */
@Component
public class DocumentRenderer
{
    /** A JMustache parse failure, mapped to the 1-based line the editor should mark. */
    public record TemplateError(int line, String message) { }

    private static final Pattern LINE_IN_MESSAGE = Pattern.compile("@ line (\\d+)");

    private final Mustache.Compiler compiler = Mustache.compiler()
            .escapeHTML(true)
            // A missing or null key renders empty rather than throwing: Mustache semantics, and
            // what makes an injection payload inert instead of an error channel. Unknown-field
            // warnings are an editor concern (Phase 4), not a render-time failure.
            .nullValue("")
            .defaultValue("")
            // {{> rapla/nav}} — builtin partials only (PRD 097 partials step 1). Loaded lazily
            // at execute by JMustache; validate() checks references itself (see below).
            .withLoader(name -> {
                String source = BuiltinPartials.find(name);
                if (source == null)
                {
                    throw new MustacheException("Unknown partial '" + name + "'");
                }
                return new java.io.StringReader(source);
            });

    private final ConcurrentMap<String, Template> compiled = new ConcurrentHashMap<>();

    /** Render the template body against the (already §12-filtered) GraphQL data tree. */
    public String render(String templateBody, Map<String, Object> data)
    {
        return compile(templateBody).execute(data == null ? Map.of() : data);
    }

    private static final Pattern PARTIAL_REF = Pattern.compile("\\{\\{\\s*>\\s*([^}\\s]+)\\s*\\}\\}");

    /** Engine-truthful validation: empty when the real compiler accepts the body. */
    public Optional<TemplateError> validate(String templateBody)
    {
        try
        {
            compile(templateBody);
        }
        catch (MustacheException e)
        {
            return Optional.of(new TemplateError(lineOf(e), e.getMessage()));
        }
        // JMustache resolves {{> partial}} lazily at EXECUTE (its self-inclusion guard), so a
        // dangling name passes compile and would fail mid-render — or never, inside an empty
        // section. Save-time truth requires checking the references here.
        String body = templateBody == null ? "" : templateBody;
        Matcher partial = PARTIAL_REF.matcher(body);
        while (partial.find())
        {
            String name = partial.group(1);
            if (BuiltinPartials.find(name) == null)
            {
                int line = 1 + (int) body.substring(0, partial.start()).chars().filter(c -> c == '\n').count();
                return Optional.of(new TemplateError(line, "Unknown partial '" + name
                        + "' — available: " + String.join(", ", BuiltinPartials.SOURCES.keySet())));
            }
        }
        return Optional.empty();
    }

    int cacheSize()
    {
        return compiled.size();
    }

    private Template compile(String templateBody)
    {
        String body = templateBody == null ? "" : templateBody;
        return compiled.computeIfAbsent(sha256(body), key -> compiler.compile(body));
    }

    /**
     * JMustache reports the line inside the exception message ("... @line 2"). It is the only
     * place the parser exposes it, so the editor's red marker depends on this parse.
     */
    private static int lineOf(MustacheException e)
    {
        String message = e.getMessage();
        if (message != null)
        {
            Matcher m = LINE_IN_MESSAGE.matcher(message);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return 1;
    }

    private static String sha256(String value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
