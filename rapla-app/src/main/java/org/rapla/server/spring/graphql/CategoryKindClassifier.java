package org.rapla.server.spring.graphql;

import org.rapla.entities.Category;

/**
 * PRD 035 §5a — deployment-agnostic determination of a {@link Category}'s
 * render kind (VALUE_LIST / ORGANIZATION / SYSTEM). Three-tier rule, applied
 * directly to the category in question (NOT walked up to a topmost ancestor):
 *
 * <ol>
 *   <li><b>rapla-core hardcoded</b> — the super-category itself is
 *       {@code SYSTEM}; categories anywhere under the {@code user-groups}
 *       subtree get filtered out of the Category API at the resolver layer
 *       (they surface only via {@code type Group}), so we never expect
 *       to classify one — defensive {@code SYSTEM} fallback if it slips
 *       through.</li>
 *   <li><b>Admin annotation</b> — {@code category-kind} on the category
 *       itself with values {@code value-list} / {@code organization} /
 *       {@code system}. Wins over the heuristic.</li>
 *   <li><b>Heuristic</b> — category whose subtree has depth = 1 (only
 *       leaf children, no grandchildren) → {@code VALUE_LIST}. Subtree
 *       depth ≥ 2 → {@code ORGANIZATION}. A leaf node (no children)
 *       gets {@code VALUE_LIST} too — degenerate case, no rendering
 *       implication.</li>
 * </ol>
 *
 * <p>Important: kind is about THIS category's own subtree, not where it
 * lives in the super-tree. An attribute's {@code rootCategory} constraint
 * may point at a nested category (e.g.
 * {@code Root/Veranstaltungsattribute/Veranstaltungskategorien}); the
 * kind reflects whether that specific subtree is flat (enum-renderable)
 * or hierarchical (tree-renderable) — independent of its depth in super.
 *
 * <p>Zero deployment-specific names. Same code at every rapla install;
 * different output based on the admin's data.
 */
final class CategoryKindClassifier
{
    /** Category root key that rapla treats as the permission-groups subtree. */
    static final String USER_GROUPS_KEY = "user-groups";

    /** Admin annotation key for explicit kind override on a category. */
    static final String ANNOTATION_KIND = "category-kind";

    enum Kind { VALUE_LIST, ORGANIZATION, SYSTEM }

    private CategoryKindClassifier() {}

    /**
     * Classify a Category by its rendering kind, looking at THIS category's
     * own subtree shape. The position in the super-tree is not consulted —
     * an attribute's {@code rootCategory} may live anywhere and we judge
     * its subtree on its own merits. The {@code superCategory} parameter
     * is preserved for callers that want a defensive super-check; pass
     * null to skip it.
     */
    static Kind kindOf(Category c, Category superCategory)
    {
        if (c == null) return Kind.SYSTEM;
        if (superCategory != null && c == superCategory) return Kind.SYSTEM;
        // super-category itself has no parent — guard against accidentally
        // classifying it even when superCategory wasn't passed.
        if (c.getParent() == null) return Kind.SYSTEM;

        // 1. Admin annotation override (on THIS category)
        String annotated = c.getAnnotation(ANNOTATION_KIND);
        if (annotated != null)
        {
            switch (annotated.trim().toLowerCase(java.util.Locale.ROOT))
            {
                case "value-list":   return Kind.VALUE_LIST;
                case "organization": return Kind.ORGANIZATION;
                case "system":       return Kind.SYSTEM;
                // unknown annotation value → fall through to heuristic
            }
        }

        // 2. Heuristic on THIS category's subtree
        return hasGrandchildren(c) ? Kind.ORGANIZATION : Kind.VALUE_LIST;
    }

    /**
     * True if any child of {@code root} has children of its own —
     * i.e. the root subtree has depth ≥ 2.
     */
    static boolean hasGrandchildren(Category root)
    {
        if (root == null) return false;
        Category[] children = root.getCategories();
        if (children == null) return false;
        for (Category child : children)
        {
            if (child == null) continue;
            Category[] gc = child.getCategories();
            if (gc != null && gc.length > 0) return true;
        }
        return false;
    }

    /**
     * True if this category is anywhere under the {@code user-groups}
     * subtree — i.e. should be hidden from the Category API and surfaced
     * only via {@code type Group}. Used by the category(path:) and
     * categories(rootKey:) resolvers to filter the subtree out. Walks up
     * the parent chain looking for a {@code user-groups}-keyed ancestor.
     */
    static boolean isUnderUserGroups(Category c, Category superCategory)
    {
        if (c == null || superCategory == null) return false;
        Category cur = c;
        while (cur != null && cur != superCategory)
        {
            if (USER_GROUPS_KEY.equals(cur.getKey())) return true;
            cur = cur.getParent();
        }
        return false;
    }

    /**
     * Slash-separated KEY path from super-category to {@code c}. The
     * Category API uses keys, not localized names, per the consumer
     * design. Returns the empty string for super-category or null input.
     * For {@code Root/Veranstaltungsattribute/Veranstaltungskategorien}
     * (super → Veranstaltungsattribute → Veranstaltungskategorien) this
     * returns {@code "Veranstaltungsattribute/Veranstaltungskategorien"}.
     *
     * <p>Walks the parent chain until {@code getParent() == null} — super-
     * category is the only node with a null parent in rapla's model,
     * so we don't need an explicit super reference.
     */
    static String keyPath(Category c)
    {
        if (c == null || c.getParent() == null) return "";
        java.util.Deque<String> segs = new java.util.ArrayDeque<>();
        Category cur = c;
        while (cur != null && cur.getParent() != null)
        {
            segs.push(cur.getKey());
            cur = cur.getParent();
        }
        return String.join("/", segs);
    }

    /**
     * Resolve a slash-separated KEY path to a Category. Inverse of
     * {@link #keyPath}. Returns null if any segment doesn't resolve.
     * Leading {@code "Root/"} is tolerated for compatibility with stored
     * paths but optional in queries.
     */
    static Category resolveKeyPath(Category superCategory, String path)
    {
        if (superCategory == null || path == null || path.isBlank()) return null;
        String normalized = path.startsWith("Root/") ? path.substring(5)
                : "Root".equals(path) ? "" : path;
        Category cur = superCategory;
        for (String segment : normalized.split("/"))
        {
            if (segment.isBlank()) continue;
            cur = cur.getCategory(segment);
            if (cur == null) return null;
        }
        return cur == superCategory ? null : cur;
    }
}
