package org.rapla.server.spring;

import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.rest.JacksonObjectMapperFactory;
import org.rapla.rest.dto.UserListItem;
import org.rapla.storage.PermissionController;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-user recents &amp; favorites storage (PRD 089 Phase 1). Owns the JSON
 * (de)serialization of two {@link Preferences} entries (D5), the recents
 * cap/promote logic, the §12 read filter, and the D6 write-time compaction.
 *
 * <h2>Storage model (D1/D5)</h2>
 * Two distinct {@link TypedComponentRole} JSON string lists hold the
 * <b>minimum</b>: recents {@code {id,kind,ts}} (ts drives promote/order, cap 20),
 * favorites {@code {id,kind}} (insertion order, uncapped). The ids are
 * <b>opaque</b> — never entity references — so the lists stay invisible to the
 * dependency/reference graph and deleting a referenced resource needs no force
 * flag (D1).
 *
 * <h2>Read (§16-pure, D3)</h2>
 * {@link #readRecents}/{@link #readFavorites} resolve each id to the live entity,
 * build a {@link UserListItem} {@code {id,kind,label,color,typeKey}}, and DROP
 * any entry that no longer resolves OR that the caller cannot see
 * ({@code canRead} for a resource, {@code canAdminUser}/self for a user). No
 * writes on the read path.
 *
 * <h2>Write (D6)</h2>
 * Every {@code POST}/{@code DELETE} re-serializes the list and, in the same
 * patch, prunes ids that no longer resolve (opportunistic compaction) — bounding
 * prefs growth with zero delete-path code.
 */
@Service
public class UserListsService
{
    public static final TypedComponentRole<String> RECENTS =
            new TypedComponentRole<>("org.rapla.user.recents");
    public static final TypedComponentRole<String> FAVORITES =
            new TypedComponentRole<>("org.rapla.user.favorites");

    static final String KIND_RESOURCE = "resource";
    static final String KIND_USER = "user";

    private static final int RECENTS_CAP = 20;

    private final RaplaFacade facade;
    private final RaplaLocale raplaLocale;
    private final JsonMapper mapper = JacksonObjectMapperFactory.create();

    public UserListsService(RaplaFacade facade, RaplaLocale raplaLocale)
    {
        this.facade = facade;
        this.raplaLocale = raplaLocale;
    }

    private record StoredEntry(String id, String kind, Long ts) {}

    // === reads (§16-pure) ====================================================

    public List<UserListItem> readRecents(User caller) throws RaplaException
    {
        return resolveVisible(readStored(caller, RECENTS), caller);
    }

    public List<UserListItem> readFavorites(User caller) throws RaplaException
    {
        return resolveVisible(readStored(caller, FAVORITES), caller);
    }

    // === writes (D6 compaction) ==============================================

    /** Adds/promotes a recent (newest-first, re-touch promotes, cap 20), then compacts. */
    public List<UserListItem> addRecent(User caller, String id, String kind) throws RaplaException
    {
        String normalizedKind = normalizeKind(kind);
        List<StoredEntry> current = readStored(caller, RECENTS);
        List<StoredEntry> next = new ArrayList<>();
        next.add(new StoredEntry(id, normalizedKind, System.currentTimeMillis()));
        for (StoredEntry e : current)
        {
            if (!e.id().equals(id))
            {
                next.add(e);
            }
        }
        next = compact(next);
        if (next.size() > RECENTS_CAP)
        {
            next = new ArrayList<>(next.subList(0, RECENTS_CAP));
        }
        writeStored(caller, RECENTS, next);
        return resolveVisible(next, caller);
    }

    public List<UserListItem> clearRecents(User caller) throws RaplaException
    {
        writeStored(caller, RECENTS, List.of());
        return List.of();
    }

    /** Pins a favorite (insertion order, no duplicates, uncapped), then compacts. */
    public List<UserListItem> addFavorite(User caller, String id, String kind) throws RaplaException
    {
        String normalizedKind = normalizeKind(kind);
        List<StoredEntry> current = readStored(caller, FAVORITES);
        List<StoredEntry> next = new ArrayList<>();
        for (StoredEntry e : current)
        {
            if (!e.id().equals(id))
            {
                next.add(e);
            }
        }
        next.add(new StoredEntry(id, normalizedKind, null));
        next = compact(next);
        writeStored(caller, FAVORITES, next);
        return resolveVisible(next, caller);
    }

    public List<UserListItem> removeFavorite(User caller, String id) throws RaplaException
    {
        List<StoredEntry> current = readStored(caller, FAVORITES);
        List<StoredEntry> next = new ArrayList<>();
        for (StoredEntry e : current)
        {
            if (!e.id().equals(id))
            {
                next.add(e);
            }
        }
        next = compact(next);
        writeStored(caller, FAVORITES, next);
        return resolveVisible(next, caller);
    }

    // === internals ===========================================================

    private List<StoredEntry> readStored(User caller, TypedComponentRole<String> role) throws RaplaException
    {
        Preferences prefs = facade.getPreferences(caller);
        String json = prefs.getEntryAsString(role, null);
        if (json == null || json.isEmpty())
        {
            return List.of();
        }
        try
        {
            return mapper.readValue(json, new TypeReference<List<StoredEntry>>() {});
        }
        catch (Exception e)
        {
            return List.of();
        }
    }

    private void writeStored(User caller, TypedComponentRole<String> role, List<StoredEntry> entries)
            throws RaplaException
    {
        Preferences edit = facade.edit(facade.getPreferences(caller));
        if (entries.isEmpty())
        {
            edit.removeEntry(role.getId());
        }
        else
        {
            edit.putEntry(role, mapper.writeValueAsString(entries));
        }
        facade.store(edit);
    }

    /** D6 — drop entries whose id no longer resolves to a live entity. */
    private List<StoredEntry> compact(List<StoredEntry> entries)
    {
        List<StoredEntry> out = new ArrayList<>(entries.size());
        for (StoredEntry e : entries)
        {
            if (resolve(e) != null)
            {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * D3 — resolve each id to a live entity, build a {@link UserListItem}, drop
     * anything that no longer resolves or that the caller can't see (§12).
     */
    private List<UserListItem> resolveVisible(List<StoredEntry> entries, User caller)
    {
        PermissionController pc = facade.getPermissionController();
        List<UserListItem> out = new ArrayList<>(entries.size());
        for (StoredEntry e : entries)
        {
            Entity<?> entity = resolve(e);
            if (entity == null)
            {
                continue;
            }
            if (KIND_USER.equals(e.kind()))
            {
                User u = (User) entity;
                if (!isSelf(caller, u) && !PermissionController.canAdminUser(caller, u))
                {
                    continue;
                }
                out.add(new UserListItem(u.getId(), KIND_USER, userLabel(u), null, null));
            }
            else
            {
                Allocatable a = (Allocatable) entity;
                if (!pc.canRead(a, caller))
                {
                    continue;
                }
                out.add(new UserListItem(a.getId(), KIND_RESOURCE,
                        a.getName(raplaLocale.getLocale()), colorOf(a), typeKeyOf(a)));
            }
        }
        return out;
    }

    private Entity<?> resolve(StoredEntry e)
    {
        if (e == null || e.id() == null)
        {
            return null;
        }
        if (KIND_USER.equals(e.kind()))
        {
            return facade.getOperator().tryResolve(e.id(), User.class);
        }
        return facade.getOperator().tryResolve(e.id(), Allocatable.class);
    }

    private static String normalizeKind(String kind)
    {
        return KIND_USER.equals(kind) ? KIND_USER : KIND_RESOURCE;
    }

    private static boolean isSelf(User caller, User candidate)
    {
        return caller != null && candidate != null
                && caller.getId() != null && caller.getId().equals(candidate.getId());
    }

    private static String userLabel(User u)
    {
        String name = u.getName();
        return (name != null && !name.isBlank()) ? name : u.getUsername();
    }

    private static String colorOf(Allocatable a)
    {
        try
        {
            return RaplaBuilder.getColorForClassifiable(a);
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private static String typeKeyOf(Allocatable a)
    {
        Classification c = a.getClassification();
        if (c == null)
        {
            return null;
        }
        DynamicType dt = c.getType();
        return dt == null ? null : dt.getKey();
    }
}
