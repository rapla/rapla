package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.rapla.entities.Annotatable;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.server.spring.graphql.ReservationMutationController.ReservationMutationException;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.SyncStorageOperator;
import org.rapla.storage.UpdateEvent;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 057 — DynamicType (schema editor) mutations. Two roots:
 * {@code saveDynamicType} (upsert) and {@code deleteDynamicTypes} (bulk).
 *
 * <p>Admin-only by design ({@link User#isAdmin()}); DynamicTypes are
 * deployment-wide schema, not group-scoped, so the group-admin path is
 * intentionally not honored here. §12 invariants per PRD 057 §"§12":
 * caller not admin → {@code PERMISSION_DENIED}; referenced ids that
 * don't resolve → {@code REFERENCE_NOT_FOUND}.
 *
 * <p>Uses {@link CachableStorageOperator#dispatch(UpdateEvent)} directly,
 * mirroring {@link ReservationMutationController}. Re-uses that
 * controller's exception type + {@link MutationExceptionResolver}.
 *
 * <p>Hot-swap note: after dispatch, the generated
 * {@code <TypeKey>Classification} GraphQL type appears in the schema on
 * the next {@code GraphQlSchemaRebuilder} poll (~10s). The mutation
 * response returns the stored DynamicType (consistent at dispatch
 * time); the typed schema appears asynchronously.
 *
 * <p><b>v1 scope:</b> create + replace + delete. Out of scope (PRD 057
 * §"Plan" deferred items):
 * <ul>
 *   <li>Attribute valueType change with existing data migration —
 *       requires {@code AttributeImpl.commitChange} integration</li>
 *   <li>DefaultValue handling — accepts the input shape but ignores
 *       (stores no default value in v1)</li>
 *   <li>Annotation surface beyond {@code nameformat} +
 *       {@code classification-type} (rest rejected as INVALID_VALUE)</li>
 *   <li>Permission editing on the DynamicType — preserved on update
 *       (existing permissions kept); for new types only the
 *       facade-default permissions are added</li>
 * </ul>
 */
@Controller
public class DynamicTypeMutationController
{
    private static final Set<String> ALLOWED_ANNOTATION_KEYS = Set.of(
            DynamicTypeAnnotations.KEY_NAME_FORMAT,
            "nameformat_planning",
            "nameformat_export",
            "colors",
            "color");

    private final StorageOperator operator;
    private final org.rapla.server.spring.JwtUserResolver jwtUserResolver;

    public DynamicTypeMutationController(StorageOperator operator,
            org.rapla.server.spring.JwtUserResolver jwtUserResolver)
    {
        this.operator = operator;
        this.jwtUserResolver = jwtUserResolver;
    }

    @MutationMapping
    @SuppressWarnings("unchecked")
    public DynamicType saveDynamicType(@Argument("input") Map<String, Object> input,
            @Argument("expectedLastChanged") LocalDateTime expectedLastChanged) throws RaplaException
    {
        User caller = requireAdmin();
        String inputId = (String) input.get("id");
        String key = (String) input.get("key");
        if (key == null || key.isBlank())
        {
            throw new ReservationMutationException("REQUIRED", "input.key", "key is required");
        }
        if (key.startsWith("rapla:"))
        {
            throw new ReservationMutationException("INVALID_VALUE", "input.key",
                    "key must not start with 'rapla:' (rapla-internal prefix)");
        }
        String classificationTypeStr = (String) input.get("classificationType");
        if (classificationTypeStr == null)
        {
            throw new ReservationMutationException("REQUIRED", "input.classificationType",
                    "classificationType is required");
        }
        String classificationTypeAnn = switch (classificationTypeStr)
        {
            case "RESOURCE"    -> DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE;
            case "PERSON"      -> DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON;
            case "RESERVATION" -> DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION;
            default -> throw new ReservationMutationException("INVALID_VALUE",
                    "input.classificationType",
                    "unknown classificationType: " + classificationTypeStr);
        };

        DynamicTypeImpl draft;
        boolean isCreate = (inputId == null || inputId.isBlank());
        DynamicType stored = null;
        if (isCreate)
        {
            // Key collision check
            for (DynamicType existing : operator.getDynamicTypes())
            {
                if (key.equals(existing.getKey()))
                {
                    throw new ReservationMutationException("KEY_COLLISION", "input.key",
                            "DynamicType with key '" + key + "' already exists");
                }
            }
            LocalDateTime now = operator.getCurrentTimestamp();
            draft = new DynamicTypeImpl(now, now);
            ReferenceInfo<DynamicType> newId =
                    operator.createIdentifier(DynamicType.class, 1).get(0);
            draft.setId(newId.getId());
        }
        else
        {
            stored = operator.tryResolve(new ReferenceInfo<>(inputId, DynamicType.class));
            if (stored == null)
            {
                throw new ReservationMutationException("REFERENCE_NOT_FOUND", "input.id",
                        "DynamicType " + inputId + " not found");
            }
            // Optimistic concurrency
            if (expectedLastChanged != null && stored.getLastChanged() != null
                    && !expectedLastChanged.equals(stored.getLastChanged()))
            {
                throw new ReservationMutationException("CONCURRENT_MODIFICATION",
                        "expectedLastChanged",
                        "DynamicType was modified after the supplied lastChanged timestamp");
            }
            // Classification-type change is structural and would orphan data; reject
            String storedCt = stored.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
            if (storedCt != null && !storedCt.equals(classificationTypeAnn))
            {
                throw new ReservationMutationException("INVALID_VALUE",
                        "input.classificationType",
                        "classificationType change not supported (stored=" + storedCt
                                + ", input=" + classificationTypeAnn + ")");
            }
            // Key uniqueness check excluding self
            for (DynamicType existing : operator.getDynamicTypes())
            {
                if (key.equals(existing.getKey()) && !inputId.equals(existing.getId()))
                {
                    throw new ReservationMutationException("KEY_COLLISION", "input.key",
                            "DynamicType with key '" + key + "' already exists");
                }
            }
            draft = (DynamicTypeImpl) ((DynamicTypeImpl) stored).clone();
        }

        // Set key + name + classification-type
        draft.setKey(key);
        applyMultiLanguageName((Map<String, Object>) input.get("name"), draft.getName(),
                "input.name");
        draft.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, classificationTypeAnn);

        // Apply annotations (limited allow-list — v1)
        List<Map<String, Object>> annotations = (List<Map<String, Object>>) input.get("annotations");
        if (annotations != null)
        {
            applyAnnotations(annotations, draft, "input.annotations");
        }

        // Apply attributes — full replace
        applyAttributes(draft, (List<Map<String, Object>>) input.get("attributes"),
                isCreate, "input.attributes");

        // Dispatch
        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        event.addStore(draft);
        ((CachableStorageOperator) operator).dispatch(event);

        return operator.tryResolve(new ReferenceInfo<>(draft.getId(), DynamicType.class));
    }

    @MutationMapping
    public Map<String, Object> deleteDynamicTypes(@Argument("ids") List<String> ids) throws RaplaException
    {
        User caller = requireAdmin();
        if (ids == null) ids = List.of();

        List<Map<String, Object>> results = new ArrayList<>(ids.size());
        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());

        for (int i = 0; i < ids.size(); i++)
        {
            String id = ids.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", i);
            entry.put("reservation", null);
            entry.put("allocatable", null);
            entry.put("user", null);
            entry.put("errors", List.of());

            DynamicType dt = operator.tryResolve(new ReferenceInfo<>(id, DynamicType.class));
            if (dt == null)
            {
                entry.put("errors", List.of(validationError(i,
                        "REFERENCE_NOT_FOUND", "DynamicType " + id + " not found")));
                results.add(entry);
                continue;
            }
            // Check for referrers — instances of this type
            List<String> referrers = collectReferrers(dt, 20);
            if (!referrers.isEmpty())
            {
                Map<String, Object> err = validationError(i, "REFERENCE_EXISTS",
                        "DynamicType " + id + " is in use by " + referrers.size()
                                + " instance(s); delete those first");
                Map<String, Object> errExt = new LinkedHashMap<>();
                errExt.put("path", "ids[" + i + "]");
                errExt.put("code", "REFERENCE_EXISTS");
                errExt.put("message", err.get("message"));
                err = errExt;
                err.put("referrers", referrers);
                entry.put("errors", List.of(err));
                results.add(entry);
                continue;
            }
            event.putRemoveId(new ReferenceInfo<>(id, DynamicType.class));
            entry.put("deletedKind", "DYNAMIC_TYPE");
            entry.put("deletedId", id);
            results.add(entry);
        }

        boolean anyFailure = results.stream().anyMatch(
                r -> !((List<?>) r.get("errors")).isEmpty());
        if (!anyFailure && !event.isEmpty())
        {
            ((CachableStorageOperator) operator).dispatch(event);
        }

        Map<String, Object> bulk = new LinkedHashMap<>();
        bulk.put("overallStatus", anyFailure ? "REJECTED" : "SUCCESS");
        bulk.put("results", results);
        return bulk;
    }

    // ============================================================ helpers

    private User requireAdmin() throws RaplaException
    {
        // Read from the per-query RequestContext if available; fall back to a
        // direct resolve otherwise (unit-test paths bypass instrumentation).
        // Mirrors ReservationMutationController.requireCaller, but enforces
        // isAdmin per PRD 057 §12 invariant 1.
        User caller = resolveCaller();
        if (caller == null)
        {
            throw new ReservationMutationException("PERMISSION_DENIED", "",
                    "authentication required");
        }
        if (!caller.isAdmin())
        {
            throw new ReservationMutationException("PERMISSION_DENIED", "",
                    "DynamicType mutations are admin-only");
        }
        return caller;
    }

    private User resolveCaller() throws RaplaException
    {
        return jwtUserResolver.resolveCurrentUserOrNull();
    }

    private static void applyMultiLanguageName(Map<String, Object> nameInput,
            MultiLanguageName target, String path)
    {
        if (nameInput == null)
        {
            throw new ReservationMutationException("REQUIRED", path, "name is required");
        }
        String def = (String) nameInput.get("default");
        if (def == null || def.isBlank())
        {
            throw new ReservationMutationException("REQUIRED", path + ".default",
                    "name.default is required");
        }
        // Rapla convention: "en" is the default-locale key. Set it as the
        // fallback; per-locale translations may override.
        target.setName("en", def);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> translations = (List<Map<String, Object>>) nameInput.get("translations");
        if (translations != null)
        {
            for (int i = 0; i < translations.size(); i++)
            {
                Map<String, Object> tr = translations.get(i);
                String locale = (String) tr.get("locale");
                String value = (String) tr.get("value");
                if (locale == null || locale.isBlank())
                {
                    throw new ReservationMutationException("REQUIRED",
                            path + ".translations[" + i + "].locale",
                            "translation locale is required");
                }
                target.setName(locale, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyAttributes(DynamicTypeImpl draft, List<Map<String, Object>> attrs,
            boolean isCreate, String pathBase) throws RaplaException
    {
        if (attrs == null || attrs.isEmpty())
        {
            throw new ReservationMutationException("REQUIRED", pathBase,
                    "at least one attribute is required");
        }
        // Full-replace: remove existing attributes, add new ones in order
        List<Attribute> existing = new ArrayList<>();
        for (Attribute a : draft.getAttributeIterable()) existing.add(a);
        for (Attribute e : existing) draft.removeAttribute(e);

        Set<String> seenKeys = new HashSet<>();
        for (int i = 0; i < attrs.size(); i++)
        {
            Map<String, Object> ai = attrs.get(i);
            String path = pathBase + "[" + i + "]";
            String attrKey = (String) ai.get("key");
            if (attrKey == null || attrKey.isBlank())
            {
                throw new ReservationMutationException("REQUIRED", path + ".key",
                        "attribute key is required");
            }
            if (!seenKeys.add(attrKey))
            {
                throw new ReservationMutationException("KEY_COLLISION", path + ".key",
                        "attribute key '" + attrKey + "' duplicated within DynamicType");
            }
            String valueTypeStr = (String) ai.get("valueType");
            String multStr = (String) ai.get("multiplicity");
            Boolean required = (Boolean) ai.get("required");
            if (valueTypeStr == null)
            {
                throw new ReservationMutationException("REQUIRED", path + ".valueType",
                        "attribute valueType is required");
            }
            if (multStr == null)
            {
                throw new ReservationMutationException("REQUIRED", path + ".multiplicity",
                        "attribute multiplicity is required");
            }
            AttributeType type = mapAttributeType(valueTypeStr, path + ".valueType");
            validateMultiplicity(type, multStr, path + ".multiplicity");

            AttributeImpl attr = new AttributeImpl(type);
            String attrInputId = (String) ai.get("id");
            if (attrInputId != null && !attrInputId.isBlank())
            {
                attr.setId(attrInputId);
            }
            else
            {
                ReferenceInfo<Attribute> newId =
                        operator.createIdentifier(Attribute.class, 1).get(0);
                attr.setId(newId.getId());
            }
            attr.setKey(attrKey);
            applyMultiLanguageName((Map<String, Object>) ai.get("name"), attr.getName(),
                    path + ".name");

            // multiplicity → constraint flags
            switch (multStr)
            {
                case "SINGLE" -> { /* defaults: not multi, not belongsTo, not package */ }
                case "LIST" -> attr.setConstraint(ConstraintIds.KEY_MULTI_SELECT, "true");
                case "BELONGS_TO" -> attr.setConstraint(ConstraintIds.KEY_BELONGS_TO, "true");
                case "PACKAGE" -> attr.setConstraint(ConstraintIds.KEY_PACKAGE, "true");
                default -> throw new ReservationMutationException("INVALID_VALUE",
                        path + ".multiplicity", "unknown multiplicity: " + multStr);
            }

            // required → annotation
            if (required != null && required)
            {
                attr.setAnnotation("rapla:required", "true");
            }

            // CATEGORY-only: root category
            String rootCategoryId = (String) ai.get("rootCategoryId");
            if (rootCategoryId != null && !rootCategoryId.isBlank())
            {
                if (type != AttributeType.CATEGORY)
                {
                    throw new ReservationMutationException("INVALID_VALUE",
                            path + ".rootCategoryId",
                            "rootCategoryId is only valid on CATEGORY attributes");
                }
                Category root = operator.tryResolve(new ReferenceInfo<>(rootCategoryId, Category.class));
                if (root == null)
                {
                    throw new ReservationMutationException("REFERENCE_NOT_FOUND",
                            path + ".rootCategoryId",
                            "Category " + rootCategoryId + " not found");
                }
                attr.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, root);
            }

            // ALLOCATABLE-only: expected type
            String expectedTypeKey = (String) ai.get("expectedTypeKey");
            if (expectedTypeKey != null && !expectedTypeKey.isBlank())
            {
                if (type != AttributeType.ALLOCATABLE)
                {
                    throw new ReservationMutationException("INVALID_VALUE",
                            path + ".expectedTypeKey",
                            "expectedTypeKey is only valid on ALLOCATABLE attributes");
                }
                DynamicType expected = resolveByKey(expectedTypeKey);
                if (expected == null)
                {
                    throw new ReservationMutationException("REFERENCE_NOT_FOUND",
                            path + ".expectedTypeKey",
                            "DynamicType with key '" + expectedTypeKey + "' not found");
                }
                attr.setConstraint(ConstraintIds.KEY_DYNAMIC_TYPE, expected);
            }

            // defaultValue input is accepted but ignored in v1 (PRD 057 §"Plan"
            // deferred — DefaultValueInput coercion against attribute type is
            // a separate slice).

            // attribute-level annotations: ignored in v1 (per allow-list policy)

            draft.addAttribute(attr);
        }
    }

    private void applyAnnotations(List<Map<String, Object>> annotations, Annotatable target,
            String pathBase) throws org.rapla.entities.IllegalAnnotationException
    {
        for (int i = 0; i < annotations.size(); i++)
        {
            Map<String, Object> a = annotations.get(i);
            String key = (String) a.get("key");
            String value = (String) a.get("value");
            if (key == null || key.isBlank())
            {
                throw new ReservationMutationException("REQUIRED",
                        pathBase + "[" + i + "].key",
                        "annotation key is required");
            }
            if (!ALLOWED_ANNOTATION_KEYS.contains(key))
            {
                throw new ReservationMutationException("INVALID_VALUE",
                        pathBase + "[" + i + "].key",
                        "annotation key '" + key + "' not in v1 allow-list: "
                                + ALLOWED_ANNOTATION_KEYS);
            }
            target.setAnnotation(key, value);
        }
    }

    private static AttributeType mapAttributeType(String valueType, String path)
    {
        return switch (valueType)
        {
            case "STRING"      -> AttributeType.STRING;
            case "INT"         -> AttributeType.INT;
            case "BOOLEAN"     -> AttributeType.BOOLEAN;
            case "DATE"        -> AttributeType.DATE;
            case "CATEGORY"    -> AttributeType.CATEGORY;
            case "ALLOCATABLE" -> AttributeType.ALLOCATABLE;
            default -> throw new ReservationMutationException("INVALID_VALUE", path,
                    "unknown valueType: " + valueType);
        };
    }

    private static void validateMultiplicity(AttributeType type, String mult, String path)
    {
        boolean ok = switch (type)
        {
            case STRING, INT, BOOLEAN, DATE -> "SINGLE".equals(mult);
            case CATEGORY -> "SINGLE".equals(mult) || "LIST".equals(mult);
            case ALLOCATABLE -> "SINGLE".equals(mult) || "LIST".equals(mult)
                    || "BELONGS_TO".equals(mult) || "PACKAGE".equals(mult);
        };
        if (!ok)
        {
            throw new ReservationMutationException("INVALID_VALUE", path,
                    "multiplicity " + mult + " is not allowed on valueType " + type);
        }
    }

    private DynamicType resolveByKey(String key) throws RaplaException
    {
        for (DynamicType dt : operator.getDynamicTypes())
        {
            if (key.equals(dt.getKey())) return dt;
        }
        return null;
    }

    private List<String> collectReferrers(DynamicType dt, int cap) throws RaplaException
    {
        String classificationType = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if (classificationType == null) return List.of();

        // An empty-rule filter bound to dt matches every instance of that type.
        ClassificationFilter[] ofType = { dt.newClassificationFilter() };
        boolean isReservation = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION
                .equals(classificationType);

        Collection<? extends Entity> referencing;
        if (isReservation)
        {
            // Owner-driven enumeration: passing every user as owner walks all
            // reservations (each has an owner), so the type filter catches them
            // regardless of allocatable bindings — an allocatables-only scan
            // would miss un-allocated events. null/null window = unbounded. The
            // rare appointment-less or template event is still backstopped by
            // checkNoDependencies at dispatch (DependencyException), so integrity
            // holds either way.
            User[] allUsers = operator.getUsers().toArray(new User[0]);
            referencing = ((SyncStorageOperator) operator)
                    .getReservationsSync(null, null, allUsers, null, null, ofType);
        }
        else
        {
            // Resource / person — the existing type-filtered accessor.
            referencing = operator.getAllocatables(ofType);
        }

        // maxPerType is applied here, internally: cap the referrer sample.
        List<String> referrers = new ArrayList<>();
        for (Entity e : referencing)
        {
            if (e == null) continue;
            referrers.add(e.getId());
            if (referrers.size() >= cap) break;
        }
        return referrers;
    }

    private static Map<String, Object> validationError(int idx, String code, String message)
    {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("path", "ids[" + idx + "]");
        e.put("code", code);
        e.put("message", message);
        return e;
    }
}
