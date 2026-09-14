package org.rapla.storage.dbrm;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny JSON-object codec for the client-side {@link TokenStore} — a flat
 * {@code {"key":"value",...}} document shared by the file and JNLP backends.
 *
 * <p>Deliberately dependency-free and minimal: every value rapla stores here
 * (an RSA-signed JWT, an ISO language code, an OAuth provider id) is
 * quote-free and backslash-free, so naive concatenation on write and a
 * {@code "k":"v"}-pair regex on read are sufficient and safe. Anything that
 * doesn't parse yields an empty map — the store then behaves as "nothing
 * cached", per the never-throws contract.
 */
final class TokenStoreCodec
{
    private TokenStoreCodec() {}

    private static final Pattern PAIR = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    static Map<String, String> parse(String json)
    {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null) return out;
        Matcher m = PAIR.matcher(json);
        while (m.find())
        {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    static String toJson(Map<String, String> map)
    {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet())
        {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) continue;
            if (!first) b.append(',');
            first = false;
            b.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
        }
        return b.append('}').toString();
    }
}
