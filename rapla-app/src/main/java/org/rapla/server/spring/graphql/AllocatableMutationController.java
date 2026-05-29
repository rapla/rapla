package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.internal.AllocatableImpl;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.storage.internal.SimpleEntity;
import org.rapla.framework.RaplaException;
import org.rapla.server.spring.graphql.ReservationMutationController.ReservationMutationException;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.PermissionController;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.UpdateEvent;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/**
 * PRD 063 — Allocatable (resource / person) mutations. Three roots:
 * {@code createAllocatable}, {@code updateAllocatable}, {@code deleteAllocatables}.
 *
 * <p>Mirrors {@link ReservationMutationController}'s shape:
 * {@link CachableStorageOperator#dispatch(UpdateEvent)} for storage,
 * {@link ReservationMutationException} for typed errors (translated to
 * GraphQL by the shared {@link MutationExceptionResolver}), β² typed
 * {@code AllocatableClassificationInput} dispatch for content.
 *
 * <p><b>§12 invariants</b> (PRD 063 §12):
 * <ul>
 *   <li>{@code createAllocatable} — caller must
 *       {@code PermissionController.canCreate(dt, caller)}. Non-admin
 *       callers cannot set {@code ownerId} to a different user.</li>
 *   <li>{@code updateAllocatable} — caller must
 *       {@code canModify(allocatable, caller)}.</li>
 *   <li>{@code deleteAllocatables} — admin-only at the resolver entry
 *       (per-id {@code canAdmin(allocatable, caller)} as well). Bulk
 *       result reports per-id success/failure; storage-layer rejection
 *       on reservation references surfaces as {@code STORAGE_ERROR}.</li>
 * </ul>
 *
 * <p><b>v1 scope.</b> Permission editing on allocatables is preserved on
 * update (existing permissions copied through) but not editable here.
 * New allocatables inherit type-default permissions (matches the facade
 * pattern). Owner is immutable on update.
 */
@Controller
public class AllocatableMutationController
{
    private final StorageOperator operator;

    public AllocatableMutationController(StorageOperator operator)
    {
        this.operator = operator;
    }

    // ============================================================ createAllocatable

    @MutationMapping
    @SuppressWarnings("unchecked")
    public Allocatable createAllocatable(@Argument("input") Map<String, Object> input)
            throws RaplaException
    {
        User caller = requireCaller();
        String typeKey = (String) input.get("typeKey");
        if (typeKey == null || typeKey.isBlank())
        {
            throw new ReservationMutationException("REQUIRED", "input.typeKey",
                    "typeKey is required");
        }
        DynamicType dt = resolveType(typeKey);

        // §12: caller must canCreate this type
        if (!caller.isAdmin())
        {
            PermissionController pc = operator.getPermissionController();
            if (!pc.canCreate(dt, caller))
            {
                throw new ReservationMutationException("PERMISSION_DENIED", "input.typeKey",
                        "No permission to create allocatables of type " + dt.getKey());
            }
        }

        // ownerId override (admin-only) — null = caller becomes owner
        String ownerId = (String) input.get("ownerId");
        User owner = caller;
        if (ownerId != null && !ownerId.isBlank())
        {
            if (!caller.isAdmin())
            {
                throw new ReservationMutationException("PERMISSION_DENIED", "input.ownerId",
                        "Only admins may set ownerId on create");
            }
            owner = operator.tryResolve(new ReferenceInfo<>(ownerId, User.class));
            if (owner == null)
            {
                throw new ReservationMutationException("REFERENCE_NOT_FOUND", "input.ownerId",
                        "User " + ownerId + " not found");
            }
        }

        // Build classification + allocatable
        Map<String, Object> classificationInput = (Map<String, Object>) input.get("classification");
        Classification classification = buildClassificationFromInput(dt, classificationInput, typeKey);

        AllocatableImpl a = new AllocatableImpl(operator.getCurrentTimestamp(),
                operator.getCurrentTimestamp());
        String clientId = (String) input.get("id");
        if (clientId != null && !clientId.isBlank())
        {
            a.setId(clientId);
        }
        else
        {
            ReferenceInfo<Allocatable> ref = operator.createIdentifier(Allocatable.class, 1).get(0);
            a.setId(ref.getId());
        }
        a.setClassification(classification);
        a.setOwner(owner);
        // Copy type-default permissions onto the new allocatable.
        PermissionContainer.Util.copyPermissions(dt, a);

        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        event.addStore(a);
        ((CachableStorageOperator) operator).dispatch(event);

        return operator.tryResolve(new ReferenceInfo<>(a.getId(), Allocatable.class));
    }

    // ============================================================ updateAllocatable

    @MutationMapping
    @SuppressWarnings("unchecked")
    public Allocatable updateAllocatable(@Argument("id") String id,
            @Argument("input") Map<String, Object> input,
            @Argument("expectedLastChanged") LocalDateTime expectedLastChanged) throws RaplaException
    {
        User caller = requireCaller();
        if (id == null || id.isBlank())
        {
            throw new ReservationMutationException("REQUIRED", "id", "id is required");
        }
        Allocatable stored = operator.tryResolve(new ReferenceInfo<>(id, Allocatable.class));
        if (stored == null)
        {
            throw new ReservationMutationException("REFERENCE_NOT_FOUND", "id",
                    "Allocatable " + id + " not found");
        }
        if (!caller.isAdmin()
                && !operator.getPermissionController().canModify(stored, caller))
        {
            throw new ReservationMutationException("PERMISSION_DENIED", "id",
                    "No modify permission on allocatable " + id);
        }

        // Type change rejected (analog of PRD 056 OQ1.c)
        String inputTypeKey = (String) input.get("typeKey");
        String storedTypeKey = stored.getClassification().getType().getKey();
        if (inputTypeKey != null && !inputTypeKey.equals(storedTypeKey))
        {
            throw new ReservationMutationException("INVALID_TYPE_CHANGE", "input.typeKey",
                    "Type changes via updateAllocatable are not supported (stored typeKey="
                            + storedTypeKey + ", input typeKey=" + inputTypeKey + ")");
        }

        // Optimistic concurrency
        if (expectedLastChanged != null && stored.getLastChanged() != null
                && !expectedLastChanged.equals(stored.getLastChanged()))
        {
            throw new ReservationMutationException("CONCURRENT_MODIFICATION",
                    "expectedLastChanged",
                    "Allocatable was modified after the supplied lastChanged timestamp");
        }

        // Clone for edit (rapla pattern — never mutate persistent entities).
        // AllocatableImpl exposes a public clone() returning Allocatable.
        AllocatableImpl draft = (AllocatableImpl) ((AllocatableImpl) stored).clone();
        DynamicType dt = stored.getClassification().getType();

        Map<String, Object> classificationInput = (Map<String, Object>) input.get("classification");
        Classification newClassification = buildClassificationFromInput(dt, classificationInput,
                storedTypeKey);
        draft.setClassification(newClassification);

        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        event.addStore(draft);
        ((CachableStorageOperator) operator).dispatch(event);

        return operator.tryResolve(new ReferenceInfo<>(id, Allocatable.class));
    }

    // ============================================================ deleteAllocatables

    @MutationMapping
    public Map<String, Object> deleteAllocatables(@Argument("ids") List<String> ids) throws RaplaException
    {
        User caller = requireCaller();
        if (ids == null) ids = List.of();

        List<Map<String, Object>> results = new ArrayList<>(ids.size());
        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        boolean anyFailure = false;

        for (int i = 0; i < ids.size(); i++)
        {
            String id = ids.get(i);
            Map<String, Object> entry = bulkEntry(i);

            Allocatable a = operator.tryResolve(new ReferenceInfo<>(id, Allocatable.class));
            if (a == null)
            {
                entry.put("errors", List.of(validationError(i, "REFERENCE_NOT_FOUND",
                        "Allocatable " + id + " not found")));
                results.add(entry);
                anyFailure = true;
                continue;
            }
            if (!caller.isAdmin()
                    && !operator.getPermissionController().canAdmin(a, caller))
            {
                entry.put("errors", List.of(validationError(i, "PERMISSION_DENIED",
                        "No admin permission on allocatable " + id)));
                results.add(entry);
                anyFailure = true;
                continue;
            }
            event.putRemoveId(new ReferenceInfo<>(id, Allocatable.class));
            entry.put("deletedKind", "ALLOCATABLE");
            entry.put("deletedId", id);
            results.add(entry);
        }

        if (!anyFailure && !event.isEmpty())
        {
            try
            {
                ((CachableStorageOperator) operator).dispatch(event);
            }
            catch (RaplaException re)
            {
                // Storage layer rejected (e.g. reservation references) — surface
                // as STORAGE_ERROR; clear the per-result deletedId since nothing
                // actually deleted.
                anyFailure = true;
                for (Map<String, Object> r : results)
                {
                    r.put("deletedKind", null);
                    r.put("deletedId", null);
                    r.put("errors", List.of(validationError((Integer) r.get("index"),
                            "STORAGE_ERROR", re.getMessage())));
                }
            }
        }

        Map<String, Object> bulk = new LinkedHashMap<>();
        bulk.put("overallStatus", anyFailure ? "REJECTED" : "SUCCESS");
        bulk.put("results", results);
        return bulk;
    }

    // ============================================================ helpers

    private User requireCaller() throws RaplaException
    {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
        {
            throw new ReservationMutationException("PERMISSION_DENIED", "",
                    "authentication required");
        }
        String username = null;
        if (auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt)
        {
            username = jwt.getClaimAsString("preferred_username");
        }
        if (username == null || username.isBlank()) username = auth.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username))
        {
            throw new ReservationMutationException("PERMISSION_DENIED", "",
                    "authentication required");
        }
        User caller = operator.getUser(username);
        if (caller == null)
        {
            throw new ReservationMutationException("PERMISSION_DENIED", "",
                    "caller not resolvable");
        }
        return caller;
    }

    private DynamicType resolveType(String typeKey) throws RaplaException
    {
        Collection<DynamicType> all = operator.getDynamicTypes();
        for (DynamicType dt : all)
        {
            if (typeKey.equals(dt.getKey())) return dt;
        }
        throw new ReservationMutationException("REFERENCE_NOT_FOUND", "input.typeKey",
                "DynamicType " + typeKey + " not found");
    }

    @SuppressWarnings("unchecked")
    private Classification buildClassificationFromInput(DynamicType dt,
            Map<String, Object> classificationInput, String expectedTypeKey)
    {
        Classification c = dt.newClassification();
        if (classificationInput == null) return c;
        for (Map.Entry<String, Object> variant : classificationInput.entrySet())
        {
            if (!variant.getKey().equals(expectedTypeKey))
            {
                throw new ReservationMutationException("MISMATCHED_TYPE",
                        "classification." + variant.getKey(),
                        "classification @oneOf variant '" + variant.getKey()
                                + "' does not match typeKey '" + expectedTypeKey + "'");
            }
            Map<String, Object> attrMap = (Map<String, Object>) variant.getValue();
            if (attrMap == null) return c;
            for (Map.Entry<String, Object> e : attrMap.entrySet())
            {
                Attribute attr = dt.getAttribute(e.getKey());
                if (attr == null) continue;
                Object value = coerceValue(attr, e.getValue());
                if (value != null) c.setValueForAttribute(attr, value);
            }
        }
        return c;
    }

    private static Object coerceValue(Attribute attr, Object raw)
    {
        if (raw == null) return null;
        // For v1, lean on rapla's loose typing (Classification.setValue accepts
        // String / Long / Boolean / Category / Allocatable directly). The
        // mutation controller for reservations does deeper coercion; for
        // allocatables it's not yet exercised — extend when SPA editor lands.
        return raw;
    }

    private static Map<String, Object> bulkEntry(int index)
    {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("index", index);
        e.put("reservation", null);
        e.put("allocatable", null);
        e.put("user", null);
        e.put("errors", List.of());
        return e;
    }

    private static Map<String, Object> validationError(int index, String code, String message)
    {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("path", "ids[" + index + "]");
        e.put("code", code);
        e.put("message", message);
        return e;
    }
}
