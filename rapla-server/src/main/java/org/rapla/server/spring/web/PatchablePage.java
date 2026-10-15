package org.rapla.server.spring.web;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.function.Function;

/**
 * A server-rendered page (login, start, status) whose built-in Mustache template the deployment patch
 * directory (PRD 112) can restyle — a CSS file, handed to the template as {@code patchCss} — or replace
 * with a {@code .mustache} file. Both live in the core namespace folder {@code <patch-dir>/rapla/}, are
 * re-read when their modification time changes, and an absent or broken one falls back to the built-in.
 */
public final class PatchablePage
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PatchablePage.class);

    /** The patch directory's namespace folder for files the core reads by fixed name (PRD 133 D1). */
    public static final String CORE_NAMESPACE = "rapla";

    /** Escaping is HtmlUtils (named entities), as the pages always rendered; a missing key renders empty. */
    private static final Mustache.Compiler COMPILER = Mustache.compiler().escapeHTML(true).withEscaper(HtmlUtils::htmlEscape)
            .nullValue("").defaultValue("");

    private final String builtinResource;
    private final PatchFile<String> patchCss;
    private final PatchFile<Template> patchTemplate;
    private volatile Template builtin;

    public PatchablePage(String builtinResource, Path patchDir, String templateName, String cssName)
    {
        this.builtinResource = builtinResource;
        Path dir = patchDir.resolve(CORE_NAMESPACE);
        this.patchCss = new PatchFile<>(dir.resolve(cssName), css -> css);
        this.patchTemplate = new PatchFile<>(dir.resolve(templateName), COMPILER::compile);
    }

    public String render(Map<String, Object> model)
    {
        model.put("patchCss", patchCss.get());
        Template patched = patchTemplate.get();
        return (patched != null ? patched : builtin()).execute(model);
    }

    private Template builtin()
    {
        Template t = builtin;
        if (t == null)
        {
            try (Reader reader = new InputStreamReader(new ClassPathResource(builtinResource).getInputStream(), StandardCharsets.UTF_8))
            {
                t = COMPILER.compile(reader);
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
            builtin = t;
        }
        return t;
    }

    /** A file in the patch directory, parsed again whenever its modification time changes; null while it is absent or broken. */
    private static final class PatchFile<T>
    {
        private record Loaded<T>(FileTime modified, T value) {}

        private final Path path;
        private final Function<String, T> parse;
        private volatile Loaded<T> loaded;

        PatchFile(Path path, Function<String, T> parse)
        {
            this.path = path;
            this.parse = parse;
        }

        T get()
        {
            try
            {
                FileTime modified = Files.getLastModifiedTime(path);
                Loaded<T> current = loaded;
                if (current == null || !current.modified().equals(modified))
                {
                    current = new Loaded<>(modified, parse.apply(Files.readString(path)));
                    loaded = current;
                }
                return current.value();
            }
            catch (NoSuchFileException e)
            {
                return null;
            }
            catch (IOException | RuntimeException e)
            {
                LOGGER.warn("Ignoring {}: {}", path, e.toString());
                return null;
            }
        }
    }
}
