 /*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copyReservations of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.components.util;

 import java.text.Normalizer;
 import java.util.ArrayList;
 import java.util.List;
 import java.util.Set;

/** miscellaneous util methods.*/
public abstract class Tools
{
    
    /**  same as new Object[0].*/
    public static final Object[] EMPTY_ARRAY = new Object[0];
    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class[0];
    public static final String[] EMPTY_STRING_ARRAY = new String[0];
    
    /** test if 2 char arrays match. */
    public static boolean match(char[] p1, char[] p2) {
        boolean bMatch = true;
        if (p1.length == p2.length) {
            for (int i = 0; i<p1.length; i++) {
                if (p1[i] != p2[i]) {
                    bMatch = false;
                    break;
                }
            }
        } else {
            bMatch = false;
        }
        return bMatch;
    }

    private static boolean isAsciiLetter(char c)
    {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isAsciiDigit(char c)
    {
        return c >= '0' && c <= '9';
    }

    /**
     * GraphQL identifier spec: {@code [A-Za-z_][A-Za-z0-9_]*}. Used by
     * rapla as the universal key-shape rule for DynamicType keys,
     * Attribute keys, and Category keys — every key surfaced via the
     * generated GraphQL schema must match this pattern. PRD 058.
     */
    public static boolean isSpecCompliant(String key)
    {
        if (key == null || key.isEmpty()) return false;
        if (key.equals("true") || key.equals("false") || key.equals("null")) return false;
        if (endsWithReservedGraphqlSuffix(key)) return false;
        char first = key.charAt(0);
        if (!isAsciiLetter(first) && first != '_') return false;
        for (int i = 1; i < key.length(); i++)
        {
            char c = key.charAt(i);
            if (!(isAsciiLetter(c) || isAsciiDigit(c) || c == '_')) return false;
        }
        return true;
    }

    /**
     * PRD 056 §9 — minimal syntax rule for client-supplied entity ids:
     * ASCII alphanumerics + hyphen, alphanumeric first char, length 8-64
     * ({@code [A-Za-z0-9][A-Za-z0-9-]{7,63}}). Deliberately NOT a UUID
     * structure check — rapla ids are opaque strings (legacy prefixes
     * {@code r…}/{@code u…} are not valid UUID hex). The charset excludes
     * {@code ;} (conflict composite-id separator), whitespace, and anything
     * needing XML/URL escaping; the length cap keeps composite conflict ids
     * within their VARCHAR(255) columns. Enforced at the dispatch choke
     * point for NEW Reservation / Appointment / Allocatable entities only —
     * legacy store ids (e.g. {@code period_1}) stay valid on update.
     */
    public static boolean isValidEntityId(String id)
    {
        if (id == null) return false;
        int length = id.length();
        if (length < 8 || length > 64) return false;
        char first = id.charAt(0);
        if (!isAsciiLetter(first) && !isAsciiDigit(first)) return false;
        for (int i = 1; i < length; i++)
        {
            char c = id.charAt(i);
            if (!(isAsciiLetter(c) || isAsciiDigit(c) || c == '-')) return false;
        }
        return true;
    }

    /**
     * PRD 058 — fold a candidate string into a deterministic GraphQL-spec
     * key. Pipeline:
     * <ol>
     *   <li>NFKD-normalize and drop combining marks (decomposes most diacritics)</li>
     *   <li>Explicit German fold (NFKD doesn't decompose ß / umlauts on every JVM)</li>
     *   <li>Replace every char outside {@code [A-Za-z0-9_]} with {@code _}</li>
     *   <li>If first char is not {@code [A-Za-z_]}, prefix with {@code _}</li>
     *   <li>If empty after step 3, return {@code "_"}</li>
     *   <li>If the result collides with {@code taken}, append {@code _2}, {@code _3}, … until unique</li>
     * </ol>
     * Callers that don't care about uniqueness pass {@link java.util.Collections#emptySet()}.
     */
    /**
     * Suffix words the GraphQL schema generator appends to admin keys
     * ({@code raumClassification}, {@code raumWhere}, {@code farbeEnum}, …).
     * A key may not END with one of them, so no key can impersonate a
     * generated name of another key ({@code intern} + {@code EnumWhere} vs.
     * {@code internEnum} + {@code Where}). {@code Rapla} is reserved for future
     * generated families. Hand-written schema types obey the same rule
     * (see {@code GeneratedNameNamespaceArchitectureTest}).
     */
    public static final Set<String> RESERVED_GRAPHQL_SUFFIXES = Set.of("Classification", "Where", "Enum", "Rapla");

    /** DynamicType keys whose {@code <key>Where} would shadow a hand-written predicate type. */
    private static final Set<String> RESERVED_TYPE_KEYS = Set.of("String", "Int", "Boolean", "LocalDateTime", "Category", "Allocatable");

    /** Attribute keys that would shadow the fixed fields of generated classification / where types. */
    private static final Set<String> RESERVED_ATTRIBUTE_KEYS = Set.of("typeKey", "type", "AND", "OR", "NOT");

    public static boolean endsWithReservedGraphqlSuffix(String key)
    {
        if (key == null) return false;
        for (String suffix : RESERVED_GRAPHQL_SUFFIXES)
        {
            if (key.endsWith(suffix)) return true;
        }
        return false;
    }

    public static boolean isReservedTypeKey(String key)
    {
        return key != null && RESERVED_TYPE_KEYS.contains(key);
    }

    public static boolean isReservedAttributeKey(String key)
    {
        return key != null && RESERVED_ATTRIBUTE_KEYS.contains(key);
    }

    public static String toSpecKey(String input, Set<String> taken)
    {
        String candidate = toSpecKeyCore(input);
        while (endsWithReservedGraphqlSuffix(candidate)) candidate = candidate + "_";
        if (taken == null || taken.isEmpty() || !taken.contains(candidate)) return candidate;
        for (int suffix = 2; ; suffix++)
        {
            String c = candidate + "_" + suffix;
            if (!taken.contains(c)) return c;
        }
    }

    private static String toSpecKeyCore(String input)
    {
        if (input == null || input.isEmpty()) return "_";
        // 1. Explicit German fold first — NFKD would otherwise decompose 'ü' to
        // 'u' + combining diaeresis, which we'd then drop as a combining mark,
        // turning "Prüfer" into "Prufer" instead of "Pruefer".
        StringBuilder germanFolded = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++)
        {
            char c = input.charAt(i);
            switch (c)
            {
                case 'ä': germanFolded.append("ae"); break;
                case 'ö': germanFolded.append("oe"); break;
                case 'ü': germanFolded.append("ue"); break;
                case 'Ä': germanFolded.append("Ae"); break;
                case 'Ö': germanFolded.append("Oe"); break;
                case 'Ü': germanFolded.append("Ue"); break;
                case 'ß': germanFolded.append("ss"); break;
                default:  germanFolded.append(c);
            }
        }
        // 2. NFKD for other diacritics — decomposes café → cafe + combining acute,
        // we drop the combining mark below.
        String decomposed = Normalizer.normalize(germanFolded.toString(), Normalizer.Form.NFKD);
        StringBuilder ascii = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++)
        {
            char c = decomposed.charAt(i);
            if (isAsciiLetter(c) || isAsciiDigit(c) || c == '_') ascii.append(c);
            else if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            else ascii.append('_');
        }
        if (ascii.length() == 0) return "_";
        char first = ascii.charAt(0);
        if (!isAsciiLetter(first) && first != '_') ascii.insert(0, '_');
        return ascii.toString();
    }

    /** Pre-PRD-057 behaviour for callers that want the legacy (looser) check. */
    @Deprecated
    public static boolean isKey(String key)
    {
        return isSpecCompliant(key);
    }

    private static boolean isLegacyValidStart(char c)
    {
        return c == '_' || c == '-' || Character.isLetter(c);
    }

    private static boolean isLegacyValidChar(char c)
    {
        return isLegacyValidStart(c) || Character.isDigit(c);
    }

    /**
     * Pre-PRD-058 key rule: a leading Unicode letter / {@code '_'} / {@code '-'},
     * then letters, digits, {@code '_'} or {@code '-'}. Rejects the literals
     * {@code "true"} / {@code "false"} and the empty string.
     *
     * <p>PRD 058 Phase 6 — applied <em>only</em> to the {@code user-groups}
     * Category subtree (group keys never become GraphQL identifiers, so the
     * strict {@link #isSpecCompliant} spec doesn't apply to them; relaxing to
     * this legacy rule restores the pre-058 behaviour so existing hyphen/umlaut
     * group keys stay editable). The length cap (≤ 50) is enforced by callers
     * (e.g. {@code DynamicTypeImpl.checkKey}).
     */
    public static boolean isLegacyKey(String key)
    {
        if (key == null || key.isEmpty()) return false;
        if (key.equals("true") || key.equals("false")) return false;
        if (!isLegacyValidStart(key.charAt(0))) return false;
        for (int i = 0; i < key.length(); i++)
        {
            if (!isLegacyValidChar(key.charAt(i))) return false;
        }
        return true;
    }

    public static String makeValidKey(String key)
    {
        return toSpecKey(key, java.util.Collections.emptySet());
    }

    /** same as substring(0,width-1) except that it will not
        not throw an <code>ArrayIndexOutOfBoundsException</code> if string.length()&lt;width.
     */
    public static String left(String string,int width) {
        return string.substring(0, Math.min(string.length(), width -1));
    }

    /** Convert a byte array into a printable format containing aString of hexadecimal digit characters (two per byte).
     * This method is taken form the apache jakarata
     * tomcat project.
     */
    public static String convert(byte[] bytes) {
        StringBuffer sb = new StringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(convertDigit(bytes[i] >> 4));
            sb.append(convertDigit(bytes[i] & 0x0f));
        }
        return (sb.toString());
    }

    /** Convert the specified value (0-15) to the corresponding hexadecimal digit.
     * This method is taken form the apache jakarata tomcat project.
     */
    public static char convertDigit(int value) {
        value &= 0x0f;
        if (value >= 10)
            return ((char) (value - 10 + 'a'));
        else
            return ((char) (value + '0'));
    }

    public static boolean equalsOrBothNull(Object o1, Object o2) {
        if (o1 == null) {
            return o2 == null;
        } else if ( o2 == null) {
            return false;
        } else return o1.equals(o2);
    }

    /** 1.3 compatibility method */
    public static String[] split(String stringToSplit, char delimiter) {
        List<String> keys = new ArrayList<>();
        int lastIndex = 0;
        while( true ) {
            int index = stringToSplit.indexOf( delimiter,lastIndex);
            if ( index < 0)
            {
                String token = stringToSplit.substring( lastIndex  );
                if ( token.length() >= 0)
                {
                    keys.add( token );
                }
                break;
            }
            String token = stringToSplit.substring( lastIndex , index );
            keys.add( token );
            lastIndex = index + 1;
        }
        return keys.toArray( new String[] {});

    }

//    /** 1.3 compatibility method */
//    public static String replaceAll( String string, String stringToReplace, String newString ) {
//        if ( stringToReplace.equals( newString))
//            return string;
//        int length = stringToReplace.length();
//        int oldPos = 0;
//        while ( true ) {
//            int pos = string.indexOf( stringToReplace,oldPos);
//            if ( pos < 0 )
//                return string;
//
//            string = string.substring(0, pos) + newString + string.substring( pos + length);
//            oldPos = pos + 1;
//            if ( oldPos >= string.length()  )
//                return string;
//
//        }
//    }

    public static String createXssSafeString(String value) {
	    return value != null ? value.replaceAll("<","&lt;").replaceAll(">", "&gt;").replaceAll("\"", "'") : null;
	}

    public static String firstCharUp(String s)
    {
        if (s == null)
        {
            return null;
        }
        if (s.length() < 1)
        {
            return s;
        }
        final String result = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        return result;
    }

    public static String firstCharDown(String s)
    {
        if (s == null)
        {
            return null;
        }
        if (s.length() < 1)
        {
            return s;
        }
        final String result = Character.toLowerCase(s.charAt(0)) + s.substring(1);
        return result;
    }

    /** @returns null if no url */
    public static String getUrl(String value)
    {
        int httpEnd = Math.max( value.indexOf(":"), 0);
        String protocoll = value.substring(0,httpEnd);
        if ( protocoll.equalsIgnoreCase("http") || protocoll.equalsIgnoreCase("https") || protocoll.equalsIgnoreCase("file"))
        {
            return value;
        }
        return null;
        //FIXME mit richtigem URL parsing ersetzen
//
//        try
//        {
//            int httpEnd = Math.max( value.indexOf(" ")-1, value.length());
//            URL url = new URL( );
//            return url.toExternalForm();
//        }
//        catch (MalformedURLException ex)
//        {
//            return null;
//        }
    }
}