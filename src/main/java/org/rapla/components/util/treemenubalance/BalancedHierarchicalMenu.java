package org.rapla.components.util.treemenubalance;

import java.io.PrintWriter;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class BalancedHierarchicalMenu {

    private BalancedHierarchicalMenu() {}

    // ---------------------- Public API ----------------------

    public static final class MenuNode<T> {
        public enum Type { GROUP, LEAF }

        public final String label;
        public final Type type;
        public final T payload;                 // != null nur bei LEAF
        public List<MenuNode<T>> children; // nur bei GROUP genutzt

        private MenuNode(String label, Type type, T payload, List<MenuNode<T>> children) {
            this.label = label;
            this.type = type;
            this.payload = payload;
            this.children = children != null ? children : Collections.<MenuNode<T>>emptyList();
        }

        public static <T> MenuNode<T> leaf(String label, T payload) {
            return new MenuNode<T>(label, Type.LEAF, payload, null);
        }

        public static <T> MenuNode<T> group(String label, List<MenuNode<T>> children) {
            return new MenuNode<T>(label, Type.GROUP, null, children);
        }
    }

    /** Baut einen balancierten Baum mit verständlichen, eindeutigen Range-Labels. */
    public static <T> MenuNode<T> build(List<T> items,
                                        Function<T,String> namer,
                                        Locale locale,
                                        int maxPerNode) {
        if (items == null) throw new IllegalArgumentException("items must not be null");
        if (namer == null) throw new IllegalArgumentException("namer must not be null");
        if (locale == null) locale = Locale.getDefault();
        if (maxPerNode <= 1) maxPerNode = 2;

        // 1) sortieren (locale-aware)
        final Collator collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);
        final List<T> sorted = new ArrayList<T>(items);
        Collections.sort(sorted, new Comparator<T>() {
            @Override public int compare(T a, T b) {
                return collator.compare(safe(namer.apply(a)), safe(namer.apply(b)));
            }
        });

        // 2) rekursiv gruppieren
        MenuNode<T> root = buildGroup(sorted, namer, collator, maxPerNode);
        root = splitRootIfNeeded(root, maxPerNode, collator);
        return root;
    }

    // Optionale, einfache Ausgabe (kompatibel zu Deiner vorhandenen print-Variante)
    public static <T> void print(PrintWriter writer, MenuNode<T> node) {
        print(writer, node, "", true);
    }
    private static <T> void print(PrintWriter writer, MenuNode<T> node, String prefix, boolean last) {
        final String connector = prefix.isEmpty() ? "" : (last ? "└─ " : "├─ ");
        final String nextPrefix = prefix + (prefix.isEmpty() ? "" : (last ? "   " : "│  "));
        if (node.type == MenuNode.Type.LEAF) {
            writer.println(prefix + connector + "- " + node.label);
            return;
        }
        final String count = " (" + node.children.size() + ")";
        writer.println(prefix + connector + "> " + node.label + count);
        for (int i = 0; i < node.children.size(); i++) {
            print(writer, node.children.get(i), nextPrefix, i == node.children.size() - 1);
        }
    }

    // ---------------------- Internals ----------------------

    private static <T> MenuNode<T> buildGroup(List<T> items,
                                              Function<T,String> namer,
                                              Collator collator,
                                              int maxPerNode) {
        final String first = items.isEmpty() ? "" : safe(namer.apply(items.get(0)));
        final String last  = items.isEmpty() ? "" : safe(namer.apply(items.get(items.size() - 1)));
        final String rangeLabel = makeRangeLabel(first, last);

        if (items.size() <= maxPerNode) {
            // Endgruppe: direkt Leaves erzeugen
            List<MenuNode<T>> leaves = new ArrayList<MenuNode<T>>(items.size());
            for (int i = 0; i < items.size(); i++) {
                T t = items.get(i);
                leaves.add(MenuNode.leaf(safe(namer.apply(t)), t));
            }
            return MenuNode.group(rangeLabel, leaves);
        }

        // Mehr als maxPerNode -> in balancierte Teil-Gruppen schneiden
        final int groups = (int) Math.ceil(items.size() / (double) maxPerNode);
        final int chunkSize = (int) Math.ceil(items.size() / (double) groups);

        List<MenuNode<T>> children = new ArrayList<MenuNode<T>>(groups);
        int start = 0;
        while (start < items.size()) {
            int end = Math.min(start + chunkSize, items.size());
            // Sicherheitsnetz: niemals > maxPerNode im Kind
            if (end - start > maxPerNode) end = start + maxPerNode;

            List<T> slice = items.subList(start, end);
            final String childFirst = safe(namer.apply(slice.get(0)));
            final String childLast  = safe(namer.apply(slice.get(slice.size() - 1)));
            String childLabel = makeRangeLabel(childFirst, childLast);

            // Ist die Scheibe immer noch zu groß? dann rekursiv weiter teilen
            if (slice.size() > maxPerNode) {
                // tiefer teilen
                children.add(buildGroup(new ArrayList<T>(slice), namer, collator, maxPerNode));
            } else {
                // kleine Scheibe -> Endgruppe (Leaves)
                List<MenuNode<T>> leaves = new ArrayList<MenuNode<T>>(slice.size());
                for (int i = 0; i < slice.size(); i++) {
                    T t = slice.get(i);
                    leaves.add(MenuNode.leaf(safe(namer.apply(t)), t));
                }
                children.add(MenuNode.group(childLabel, leaves));
            }

            start = end;
        }

        // Ganz oben bzw. auf jeder Ebene: Range-Label des Containers
        return MenuNode.group(rangeLabel, children);
    }

    // ---------------------- Label helpers ----------------------

    private static String makeRangeLabel(String first, String last) {
        first = safe(first);
        last  = safe(last);
        if (first.length() == 0 && last.length() == 0) return "—";
        if (first.equals(last)) return abbreviateForLabel(first, 28);

        int lcp = longestCommonPrefixLen(first, last);
        // Ein sinnvolles „Anker“-Präfix nur zeigen, wenn es hilft (>= 3 Zeichen)
        String anchor = lcp >= 3 ? abbreviateForLabel(first.substring(0, lcp), 16) : "";

        String leftPart  = distinguishPart(first, lcp);
        String rightPart = distinguishPart(last,  lcp);

        // Falls trotz allem identisch, fallback auf einfache Abkürzung
        if (eqIgnoreCaseTrim(leftPart, rightPart)) {
            leftPart  = abbreviateForLabel(first, 16);
            rightPart = abbreviateForLabel(last, 16);
            anchor = "";
        } else {
            leftPart  = abbreviateForLabel(leftPart, 16);
            rightPart = abbreviateForLabel(rightPart, 16);
        }

        if (anchor.length() > 0) {
            return anchor + " … " + leftPart + " – " + rightPart;
        } else {
            return leftPart + " – " + rightPart;
        }
    }

    private static int longestCommonPrefixLen(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len && Character.toLowerCase(a.charAt(i)) == Character.toLowerCase(b.charAt(i))) {
            i++;
        }
        return i;
    }

    /** Liefert den „ersten unterscheidenden“ Teil ab lcp (springt über Trennzeichen und gibt das nächste sinnvolle Segment zurück). */
    private static String distinguishPart(String s, int fromIdx) {
        final int n = s.length();
        int i = Math.min(Math.max(fromIdx, 0), n);

        // 1) Trennzeichen überspringen
        while (i < n && isSep(s.charAt(i))) i++;

        // 2) nächstes Segment bis zum nächsten Trenner
        int start = i;
        while (i < n && !isSep(s.charAt(i))) i++;

        // wenn Segment zu kurz oder leer -> versuch noch ein zweites Wort dazuzunehmen
        int end = i;
        if (end - start < 2) {
            while (i < n && isSep(s.charAt(i))) i++;
            while (i < n && !isSep(s.charAt(i))) i++;
            end = i;
        }

        // Fallbacks
        if (end <= start) {
            // nimm zum Schluss wenigstens das letzte „Wort“
            int lastSep = lastSepIndex(s);
            if (lastSep >= 0 && lastSep + 1 < n) return s.substring(lastSep + 1);
            return s;
        }
        return s.substring(start, end);
    }

    private static boolean isSep(char c) {
        return Character.isWhitespace(c) || c=='/' || c=='-' || c=='|' || c=='_' || c==':' || c==',' || c=='·';
    }
    private static int lastSepIndex(String s) {
        for (int i = s.length() - 1; i >= 0; i--) if (isSep(s.charAt(i))) return i;
        return -1;
    }

    /** Kürzt auf sinnvolle Länge, schneidet sauber an Wort-/Trenn-Grenzen und hängt „…“ an. */
    private static String abbreviateForLabel(String s, int maxLen) {
        s = safe(s);
        if (s.length() <= maxLen) return s;
        // versuche eine schöne Grenze <= maxLen
        int cut = Math.min(maxLen, s.length());
        // rückwärts bis zum nächsten Trenner
        int i = cut;
        while (i > Math.max(6, cut - 8)) {
            if (isSep(s.charAt(i - 1))) break;
            i--;
        }
        if (i <= Math.max(6, cut - 8)) i = cut; // keine gute Grenze gefunden -> hart schneiden
        return stripDots(s.substring(0, i)) + "…";
    }

    // ---------------------- Misc helpers ----------------------

    private static String safe(String s) {
        if (s == null) return "";
        // vereinheitlichte Spaces
        String t = s.replace('\u00A0', ' ').trim();
        // Mehrfachspaces reduzieren
        StringBuilder sb = new StringBuilder(t.length());
        boolean space = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!space) sb.append(' ');
                space = true;
            } else {
                sb.append(c);
                space = false;
            }
        }
        return sb.toString();
    }

    private static boolean eqIgnoreCaseTrim(String a, String b) {
        return safe(a).equalsIgnoreCase(safe(b));
    }

    private static String stripDots(String s) {
        // entfernt auslaufende Punkte/Trenner vorm „…“
        int i = s.length();
        while (i > 0) {
            char c = s.charAt(i - 1);
            if (c == '.' || c == '-' || c == ' ' || c == '/' || c == '_' || c == '|') i--;
            else break;
        }
        return s.substring(0, i);
    }

    private static <T> MenuNode<T> splitRootIfNeeded(BalancedHierarchicalMenu.MenuNode<T> root,
                                                     int maxPerNode,
                                                     Collator collator) {
        if (root == null || root.children == null) return root;
        final List<MenuNode<T>> top = root.children;
        final int n = top.size();
        if (n <= maxPerNode) {
            return root; // nichts zu tun
        }

        // Sortierung beibehalten (sollte bereits sortiert sein, aber sicherheitshalber):
        Collections.sort(top, new Comparator<MenuNode<T>>() {
            @Override public int compare(MenuNode<T> a, MenuNode<T> b) {
                return collator.compare(a.label, b.label);
            }
        });

        // Wie viele Buckets brauchen wir mindestens?
        final int buckets = (n + maxPerNode - 1) / maxPerNode; // ceil(n / maxPerNode)

        // Gleichmäßig balancieren
        final List<MenuNode<T>> newChildren = new ArrayList<MenuNode<T>>(buckets);
        int start = 0;
        for (int i = 0; i < buckets; i++) {
            // Verteile so gleichmäßig wie möglich
            int remaining = n - start;
            int remainingBuckets = buckets - i;
            int size = Math.max(1, remaining / remainingBuckets);
            if (remaining % remainingBuckets != 0) {
                size += 0; // bereits durch ganzzahlige Division ausgeglichen
            }
            int end = Math.min(n, start + size);

            List<MenuNode<T>> slice = top.subList(start, end);
            if (!slice.isEmpty()) {
                // Label aus erstem und letztem Label der Slice bilden
                String first = slice.get(0).label;
                String last  = slice.get(slice.size() - 1).label;

                // Range-Labelling klar und eindeutig, keine "..."-Abkürzungen
                String rangeLabel = first + " – " + last;

                MenuNode<T> wrapper = MenuNode.group(rangeLabel,new ArrayList<>());
                // Kinder in neuen Wrapper verschieben
                wrapper.children.addAll(new ArrayList<MenuNode<T>>(slice));
                newChildren.add(wrapper);
            }
            start = end;
        }

        // Root-Kinder durch die neue, kompakte Ebene ersetzen
        root.children = newChildren;
        return root;
    }

}
