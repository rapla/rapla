package org.rapla.server.spring.graphql;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.internal.AllocatableImpl;
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
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(AllocatableMutationController.class);

    private final StorageOperator operator;
    private final org.rapla.server.spring.JwtUserResolver jwtUserResolver;

    private final org.rapla.server.internal.SecurityManager security;

    public AllocatableMutationController(StorageOperator operator,
            org.rapla.server.spring.JwtUserResolver jwtUserResolver,
            org.rapla.server.internal.SecurityManager security)
    {
        this.security = security;
        this.operator = operator;
        this.jwtUserResolver = jwtUserResolver;
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
        // PRD 056 §9 (2026-07-06): client id is REQUIRED — no server fallback.
        // Retry-idempotency works via ID_COLLISION on the client-minted id.
        String clientId = (String) input.get("id");
        if (clientId == null || clientId.isBlank())
        {
            throw new ReservationMutationException("REQUIRED", "input.id",
                    "id is required — clients mint their own entity ids (PRD 056 §9)");
        }
        a.setId(clientId);
        a.setClassification(classification);
        a.setOwner(owner);
        // Copy type-default permissions onto the new allocatable.
        PermissionContainer.Util.copyPermissions(dt, a);

        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        event.addStore(a);
        event.addCreate(a.getReference());
        dispatchChecked(event);

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
            // §12 (security-audit A0d): don't leak existence past read scope — a caller who can
            // neither read nor modify gets the same REFERENCE_NOT_FOUND as a nonexistent id.
            if (!operator.getPermissionController().canRead(stored, caller))
            {
                throw new ReservationMutationException("REFERENCE_NOT_FOUND", "id",
                        "Allocatable " + id + " not found");
            }
            throw new ReservationMutationException("PERMISSION_DENIED", "id",
                    "No modify permission on allocatable " + id);
        }

        // Type change accepted (PRD 096 Phase 4 — mirrors the PRD 056 OQ1.c
        // revision): @oneOf variant must match the NEW typeKey, create-gate
        // on the target type. Attribute remapping is the client's job.
        String inputTypeKey = (String) input.get("typeKey");
        String storedTypeKey = stored.getClassification().getType().getKey();
        DynamicType targetType = stored.getClassification().getType();
        String targetTypeKey = storedTypeKey;
        if (inputTypeKey != null && !inputTypeKey.equals(storedTypeKey))
        {
            targetType = resolveType(inputTypeKey);
            if (!caller.isAdmin())
            {
                PermissionController pc = operator.getPermissionController();
                if (!pc.canCreate(targetType, caller))
                {
                    throw new ReservationMutationException("PERMISSION_DENIED", "input.typeKey",
                            "No permission to create allocatables of type " + targetType.getKey());
                }
            }
            targetTypeKey = inputTypeKey;
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

        Map<String, Object> classificationInput = (Map<String, Object>) input.get("classification");
        Classification newClassification = buildClassificationFromInput(targetType, classificationInput,
                targetTypeKey);
        draft.setClassification(newClassification);

        UpdateEvent event = new UpdateEvent();
        event.setUserId(caller.getId());
        event.addStore(draft);
        dispatchChecked(event);

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
                // §12 (security-audit A0d): a caller who cannot read the allocatable must not learn
                // it exists — report REFERENCE_NOT_FOUND, identical to a nonexistent id.
                boolean readable = operator.getPermissionController().canRead(a, caller);
                entry.put("errors", List.of(readable
                        ? validationError(i, "PERMISSION_DENIED", "No admin permission on allocatable " + id)
                        : validationError(i, "REFERENCE_NOT_FOUND", "Allocatable " + id + " not found")));
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
                dispatchChecked(event);
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

    /** Same seam as {@link ReservationMutationController} — the JwtUserResolver
     *  handles external-IdP tokens; the earlier hand-rolled preferred_username
     *  lookup silently failed for Keycloak/Entra logins (dedup 2026-07-08). */
    private void dispatchChecked(UpdateEvent event) throws RaplaException
    {
        WriteGate.check(security, operator, event);
        ((CachableStorageOperator) operator).dispatch(event);
    }

    private User requireCaller()
    {
        User caller = jwtUserResolver.resolveCurrentUserOrNull();
        if (caller == null)
        {
            throw new ReservationMutationException("PERMISSION_DENIED", "caller",
                    "Mutations require an authenticated caller");
        }
        return caller;
    }

    private DynamicType resolveType(String typeKey) throws RaplaException
    {
        DynamicType dt = ClassificationInputMapper.tryResolveType(operator, typeKey);
        if (dt == null)
        {
            throw new ReservationMutationException("REFERENCE_NOT_FOUND", "input.typeKey",
                    "DynamicType " + typeKey + " not found");
        }
        return dt;
    }

    @SuppressWarnings("unchecked")
    /** Delegates to the shared {@link ClassificationInputMapper} (dedup 2026-07-08). */
    private Classification buildClassificationFromInput(DynamicType dt,
            Map<String, Object> classificationInput, String expectedTypeKey)
    {
        return ClassificationInputMapper.buildClassificationFromInput(operator, dt, classificationInput, expectedTypeKey);
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
