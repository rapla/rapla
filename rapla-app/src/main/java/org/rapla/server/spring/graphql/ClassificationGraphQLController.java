package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.RaplaException;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

/**
 * PRD 035 Cut C — GraphQL resolvers for allocatables, dynamic types, and the
 * Classification interface. Goes through {@link StorageOperator} (NOT
 * {@link org.rapla.facade.RaplaFacade}, per the 2026-05-25 decision); §12
 * filtering is inline at the output boundary, matching the pattern in
 * {@link HelloGraphQLController}.
 *
 * <p>The interface fields ({@code Classification.typeId}, {@code .type},
 * {@code .attributes}) are resolved here via {@link SchemaMapping}; the
 * GENERATED per-DynamicType implementing types (e.g. {@code RoomClassification})
 * are wired programmatically by {@link HotSwappableGraphQlSource} using
 * {@link GeneratedClassificationWiring} — their attribute fields can't carry
 * {@code @SchemaMapping} because there's no Java class to put it on (rapla
 * has no Java class per DynamicType — they're all {@code ClassificationImpl}
 * with a DynamicType pointer; see PRD 035 line 554-560).
 *
 * <p>Reference-valued attributes in classifications (CATEGORY / ALLOCATABLE)
 * carry §12: the resolved value is dropped (singular) or filtered (multi) if
 * the caller can't read the referent. PermissionController.canRead is the
 * gate on every output boundary.
 */
@Controller
public class ClassificationGraphQLController
{
    private final StorageOperator operator;

    public ClassificationGraphQLController(StorageOperator operator)
    {
        this.operator = operator;
    }

    // === Query roots ==========================================================

    @QueryMapping
    public List<Allocatable> allocatables(@Argument("filter") AllocatableFilter filter) throws RaplaException
    {
        User caller = resolveCaller();
        Collection<Allocatable> all = operator.getAllocatables(null);
        if (all == null) return List.of();
        PermissionController pc = operator.getPermissionController();
        List<Allocatable> visible = new ArrayList<>();
        for (Allocatable a : all)
        {
            if (a == null) continue;
            if (isInternalAllocatable(a)) continue;   // skip rapla-internal (template/period/etc.)
            if (caller != null && !pc.canRead(a, caller)) continue;
            if (caller == null && !isWorldReadable(a)) continue;
            if (!matches(a, filter)) continue;
            visible.add(a);
        }
        return visible;
    }

    /**
     * Allocatables whose DynamicType is rapla-internal (template entries,
     * etc.) never surface through GraphQL — see
     * {@link ClassificationSdlGenerator#isRaplaInternal}.
     */
    private static boolean isInternalAllocatable(Allocatable a)
    {
        if (a.getClassification() == null) return false;
        return ClassificationSdlGenerator.isRaplaInternal(a.getClassification().getType());
    }

    @QueryMapping
    public Allocatable allocatable(@Argument("id") String id) throws RaplaException
    {
        if (id == null || id.isBlank()) return null;
        User caller = resolveCaller();
        Allocatable a;
        try
        {
            a = operator.tryResolve(new ReferenceInfo<>(id, Allocatable.class));
        }
        catch (RuntimeException e)
        {
            return null;
        }
        if (a == null) return null;
        if (isInternalAllocatable(a)) return null;   // rapla-internal — never surface
        PermissionController pc = operator.getPermissionController();
        if (caller == null && !isWorldReadable(a)) return null;
        if (caller != null && !pc.canRead(a, caller)) return null;
        return a;
    }

    /**
     * User-visible DynamicTypes. Rapla-internal storage-scaffolding types
     * (classification-type=rapla — period / template / defaultUser /
     * anonymousEvent) are filtered out: they're never browseable through
     * GraphQL, matching the SDL generator policy (no
     * {@code Rapla*Classification} types generated). No §12 — DynamicType
     * definitions are deployment-global metadata used by form widgets.
     */
    @QueryMapping
    public List<DynamicType> types() throws RaplaException
    {
        Collection<DynamicType> all = operator.getDynamicTypes();
        if (all == null) return List.of();
        List<DynamicType> out = new ArrayList<>(all.size());
        for (DynamicType dt : all)
        {
            if (dt == null) continue;
            if (ClassificationSdlGenerator.isRaplaInternal(dt)) continue;
            out.add(dt);
        }
        return out;
    }

    @QueryMapping
    public DynamicType type(@Argument("key") String key) throws RaplaException
    {
        if (key == null || key.isBlank()) return null;
        Collection<DynamicType> all = operator.getDynamicTypes();
        if (all == null) return null;
        for (DynamicType dt : all)
        {
            if (dt != null && key.equals(dt.getKey()))
            {
                if (ClassificationSdlGenerator.isRaplaInternal(dt)) return null;
                return dt;
            }
        }
        return null;
    }

    // === Allocatable derived-field resolvers ==================================

    @SchemaMapping(typeName = "Allocatable", field = "type")
    public String allocatableType(Allocatable a)
    {
        return a.isPerson() ? "PERSON" : "RESOURCE";
    }

    @SchemaMapping(typeName = "Allocatable", field = "displayName")
    public String allocatableDisplayName(Allocatable a)
    {
        return a.getName(Locale.getDefault());
    }

    @SchemaMapping(typeName = "Allocatable", field = "owner")
    public User allocatableOwner(Allocatable a) throws RaplaException
    {
        ReferenceInfo<User> ref = a.getOwnerRef();
        if (ref == null) return null;
        return operator.tryResolve(ref);
    }

    @SchemaMapping(typeName = "Allocatable", field = "createdAt")
    public OffsetDateTime allocatableCreatedAt(Allocatable a)
    {
        LocalDateTime ts = a.getCreateDate();
        return ts == null ? null : ts.atOffset(ZoneOffset.UTC);
    }

    @SchemaMapping(typeName = "Allocatable", field = "lastModifiedAt")
    public OffsetDateTime allocatableLastModifiedAt(Allocatable a)
    {
        LocalDateTime ts = a.getLastChanged();
        return ts == null ? null : ts.atOffset(ZoneOffset.UTC);
    }

    @SchemaMapping(typeName = "Allocatable", field = "classification")
    public Classification allocatableClassification(Allocatable a)
    {
        return a.getClassification();
    }

    // === DynamicType derived-field resolvers ==================================

    @SchemaMapping(typeName = "DynamicType", field = "name")
    public String dynamicTypeName(DynamicType dt)
    {
        return dt.getName(Locale.getDefault());
    }

    @SchemaMapping(typeName = "DynamicType", field = "classificationType")
    public String dynamicTypeClassificationType(DynamicType dt)
    {
        String v = dt.getAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE);
        if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON.equals(v))      return "PERSON";
        if (DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESERVATION.equals(v)) return "RESERVATION";
        return "RESOURCE";
    }

    @SchemaMapping(typeName = "DynamicType", field = "attributes")
    public List<AttributeDescriptorDto> dynamicTypeAttributes(DynamicType dt)
    {
        Attribute[] attrs = dt.getAttributes();
        if (attrs == null) return List.of();
        List<AttributeDescriptorDto> out = new ArrayList<>(attrs.length);
        for (Attribute attr : attrs)
        {
            if (attr == null) continue;
            out.add(AttributeDescriptorDto.from(attr));
        }
        return out;
    }

    // === Classification interface field resolvers =============================
    //
    // These apply to EVERY GENERATED `<TypeKey>Classification implements
    // Classification` — interface fields are inherited at the GraphQL level,
    // so a single @SchemaMapping on the interface name does the job.

    @SchemaMapping(typeName = "Classification", field = "typeId")
    public String classificationTypeId(Classification c)
    {
        DynamicType dt = c.getType();
        return dt == null ? null : dt.getId();
    }

    @SchemaMapping(typeName = "Classification", field = "type")
    public DynamicType classificationType(Classification c)
    {
        return c.getType();
    }

    @SchemaMapping(typeName = "Classification", field = "attributes")
    public List<AttributeValueDto> classificationAttributes(Classification c)
    {
        DynamicType dt = c.getType();
        if (dt == null) return List.of();
        User caller = resolveCaller();
        PermissionController pc = operator.getPermissionController();
        List<AttributeValueDto> out = new ArrayList<>();
        for (Attribute attr : dt.getAttributes())
        {
            if (attr == null) continue;
            AttributeValueDto dto = buildAttributeValue(c, attr, caller, pc);
            if (dto != null) out.add(dto);
        }
        return out;
    }

    /** Build an {@link AttributeValueDto} for a single attribute. §12 applies
     *  to reference values (Category passes through, Allocatable filtered). */
    private AttributeValueDto buildAttributeValue(Classification c, Attribute attr,
            User caller, PermissionController pc)
    {
        String key = attr.getKey();
        AttributeType t = attr.getType();
        if (t == null) return null;
        boolean multi = isMultiSelect(attr);

        if (multi)
        {
            Collection<Object> values = c.getValues(attr);
            if (values == null || values.isEmpty()) return AttributeValueDto.bare(key);
            if (t == AttributeType.CATEGORY)
            {
                List<Category> out = new ArrayList<>(values.size());
                for (Object v : values) if (v instanceof Category cat) out.add(cat);
                return AttributeValueDto.categoryList(key, out);
            }
            if (t == AttributeType.ALLOCATABLE)
            {
                List<Allocatable> out = new ArrayList<>(values.size());
                for (Object v : values)
                {
                    if (!(v instanceof Allocatable a)) continue;
                    if (caller != null && !pc.canRead(a, caller)) continue;
                    if (caller == null && !isWorldReadable(a)) continue;
                    out.add(a);
                }
                return AttributeValueDto.allocatableList(key, out);
            }
            // No multi-select for STRING / INT / BOOLEAN / DATE in current rapla data model.
            return AttributeValueDto.bare(key);
        }

        Object v = c.getValueForAttribute(attr);
        if (v == null) return AttributeValueDto.bare(key);

        return switch (t)
        {
            case STRING      -> AttributeValueDto.stringV(key, v.toString());
            case INT         -> AttributeValueDto.intV(key,
                    v instanceof Number n ? n.longValue() : null);
            case BOOLEAN     -> AttributeValueDto.boolV(key, v instanceof Boolean b ? b : null);
            case DATE        -> AttributeValueDto.dateV(key, v instanceof LocalDateTime ldt ? ldt : null);
            case CATEGORY    -> AttributeValueDto.categoryV(key, v instanceof Category cat ? cat : null);
            case ALLOCATABLE -> {
                if (!(v instanceof Allocatable a)) yield AttributeValueDto.bare(key);
                if (caller != null && !pc.canRead(a, caller)) yield AttributeValueDto.bare(key);
                if (caller == null && !isWorldReadable(a))    yield AttributeValueDto.bare(key);
                yield AttributeValueDto.allocatableV(key, a);
            }
        };
    }

    private static boolean isMultiSelect(Attribute attr)
    {
        Object c = attr.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
        if (c == null) return false;
        if (c instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(c.toString());
    }

    // === filter predicate =====================================================

    private static boolean matches(Allocatable a, AllocatableFilter f)
    {
        if (f == null) return true;
        if (f.typeKeyEq() != null && !f.typeKeyEq().isBlank())
        {
            DynamicType dt = a.getClassification() == null ? null : a.getClassification().getType();
            if (dt == null || !f.typeKeyEq().equals(dt.getKey())) return false;
        }
        if (f.isPersonEq() != null && a.isPerson() != f.isPersonEq()) return false;
        if (f.nameContains() != null && !f.nameContains().isBlank())
        {
            String hay = a.getName(Locale.getDefault());
            if (hay == null) return false;
            if (!hay.toLowerCase().contains(f.nameContains().toLowerCase())) return false;
        }
        if (f.ownerEq() != null && !f.ownerEq().isBlank())
        {
            ReferenceInfo<User> ref = a.getOwnerRef();
            if (ref == null || !f.ownerEq().equals(ref.getId())) return false;
        }
        return true;
    }

    /**
     * Anonymous-caller read gate. For now nothing is world-readable through
     * GraphQL — anonymous callers see empty results everywhere. SecurityConfig
     * still permits unauthenticated requests to {@code /api/graphql} so the
     * schema and trivial queries (hello/serverTime/version) can be probed
     * without a token. Once we add explicit world-readable allocatables /
     * resources, replace this with a real check.
     */
    private static boolean isWorldReadable(Allocatable a)
    {
        return false;
    }

    // === auth helpers =========================================================

    private User resolveCaller()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String username = null;
        if (auth.getPrincipal() instanceof Jwt jwt)
        {
            username = jwt.getClaimAsString("preferred_username");
        }
        if (username == null || username.isBlank()) username = auth.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) return null;
        try { return operator.getUser(username); }
        catch (RaplaException e) { return null; }
    }

    // === DTOs =================================================================

    /** Mirror of the {@code AllocatableFilter} GraphQL input. */
    public record AllocatableFilter(
            String  typeKeyEq,
            Boolean isPersonEq,
            String  nameContains,
            String  ownerEq) {}

    /**
     * Mirror of the {@code AttributeDescriptor} GraphQL type. Read snapshot
     * of an Attribute's metadata; widget config flows from these fields per
     * PRD 035 §5.
     */
    public record AttributeDescriptorDto(
            String  key,
            String  name,
            String  valueType,
            String  multiplicity,
            boolean required,
            String  rootCategoryPath,
            String  expectedTypeKey)
    {
        static AttributeDescriptorDto from(Attribute attr)
        {
            AttributeType t = attr.getType();
            String valueType = t == null ? "STRING" : switch (t) {
                case STRING      -> "STRING";
                case INT         -> "INT";
                case BOOLEAN     -> "BOOLEAN";
                case DATE        -> "DATE";
                case CATEGORY    -> "CATEGORY";
                case ALLOCATABLE -> "ALLOCATABLE";
            };
            boolean multi = isMultiSelectStatic(attr);
            String rootPath = null;
            String expected = null;
            if (t == AttributeType.CATEGORY)
            {
                Object root = attr.getConstraint(ConstraintIds.KEY_ROOT_CATEGORY);
                if (root instanceof Category cat)
                {
                    rootPath = cat.getPath(null, Locale.getDefault());
                }
            }
            else if (t == AttributeType.ALLOCATABLE)
            {
                Object dt = attr.getConstraint(ConstraintIds.KEY_DYNAMIC_TYPE);
                if (dt instanceof DynamicType expectedType)
                {
                    expected = expectedType.getKey();
                }
            }
            return new AttributeDescriptorDto(
                    attr.getKey(),
                    attr.getName(Locale.getDefault()),
                    valueType,
                    multi ? "LIST" : "SINGLE",
                    !attr.isOptional(),
                    rootPath,
                    expected);
        }

        private static boolean isMultiSelectStatic(Attribute attr)
        {
            Object c = attr.getConstraint(ConstraintIds.KEY_MULTI_SELECT);
            if (c == null) return false;
            if (c instanceof Boolean b) return b;
            return "true".equalsIgnoreCase(c.toString());
        }
    }

    /**
     * Mirror of the {@code AttributeValue} GraphQL type — exactly one *Value
     * or *Values field is populated per record (or none, if the attribute is
     * unset). Built by {@link #buildAttributeValue}.
     *
     * <p>Singular variants for STRING/INT/BOOLEAN/DATE/CATEGORY/ALLOCATABLE;
     * list variants only for CATEGORY/ALLOCATABLE (only those support
     * multi-select in the current rapla data model — see
     * {@code AttributeImpl.getValidConstraintKeys}).
     */
    public record AttributeValueDto(
            String            key,
            String            stringValue,
            Long              intValue,
            Boolean           boolValue,
            LocalDateTime     dateValue,
            Category          categoryValue,
            Allocatable       allocatableValue,
            List<Category>    categoryValues,
            List<Allocatable> allocatableValues)
    {
        static AttributeValueDto bare(String key)
        {
            return new AttributeValueDto(key, null, null, null, null, null, null, null, null);
        }
        static AttributeValueDto stringV(String key, String v)
        {
            return new AttributeValueDto(key, v, null, null, null, null, null, null, null);
        }
        static AttributeValueDto intV(String key, Long v)
        {
            return new AttributeValueDto(key, null, v, null, null, null, null, null, null);
        }
        static AttributeValueDto boolV(String key, Boolean v)
        {
            return new AttributeValueDto(key, null, null, v, null, null, null, null, null);
        }
        static AttributeValueDto dateV(String key, LocalDateTime v)
        {
            return new AttributeValueDto(key, null, null, null, v, null, null, null, null);
        }
        static AttributeValueDto categoryV(String key, Category v)
        {
            return new AttributeValueDto(key, null, null, null, null, v, null, null, null);
        }
        static AttributeValueDto allocatableV(String key, Allocatable v)
        {
            return new AttributeValueDto(key, null, null, null, null, null, v, null, null);
        }
        static AttributeValueDto categoryList(String key, List<Category> v)
        {
            return new AttributeValueDto(key, null, null, null, null, null, null, v, null);
        }
        static AttributeValueDto allocatableList(String key, List<Allocatable> v)
        {
            return new AttributeValueDto(key, null, null, null, null, null, null, null, v);
        }
    }
}
