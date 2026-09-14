package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.Permission.AccessLevel;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.rest.PermissionMigrationService;
import org.rapla.rest.dto.PermissionMigrationFinding;
import org.rapla.rest.dto.PermissionMigrationFinding.PrincipalEscalation;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.impl.server.AdditiveMigrationState;
import org.rapla.storage.impl.server.SoftDenyAnalyzer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PRD 090 — admin endpoint that drains the additive-permission migration
 * worklist. The frozen allocatable ids live in the system preference
 * ({@link AdditiveMigrationState}); findings (who gains access) are recomputed
 * live from current permissions on every GET, so an edit that removes the soft
 * deny makes the entry drop off automatically.
 *
 * <p>Resolving an allocatable prunes its inert {@code DENIED} rows (making it
 * additive-clean / GraphQL-saveable) and marks it acknowledged so the
 * still-valid soft-deny grants stop nagging.
 *
 * <p>Admin-only (AGENTS.md §12): every method gates on {@link User#isAdmin()}
 * before touching anything.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
public class PermissionMigrationController implements PermissionMigrationService
{
    private final RaplaFacade facade;
    private final RemoteSession session;
    private final HttpServletRequest request;

    public PermissionMigrationController(RaplaFacade facade, RemoteSession session, HttpServletRequest request)
    {
        this.facade = facade;
        this.session = session;
        this.request = request;
    }

    @Override
    public List<PermissionMigrationFinding> getFindings() throws RaplaException
    {
        requireAdmin();
        return computeOpenFindings();
    }

    @Override
    public List<PermissionMigrationFinding> resolve(String allocatableId) throws RaplaException
    {
        requireAdmin();

        Allocatable allocatable = facade.tryResolve(new ReferenceInfo<>(allocatableId, Allocatable.class));
        if (allocatable != null && hasDeniedRow(allocatable))
        {
            Allocatable editable = facade.edit(allocatable);
            pruneDeniedRows(editable);
            facade.store(editable);
        }

        Preferences sysPrefs = facade.getSystemPreferences();
        Set<String> acknowledged = new LinkedHashSet<>(AdditiveMigrationState.readAcknowledged(sysPrefs));
        if (acknowledged.add(allocatableId))
        {
            Preferences editPrefs = facade.edit(sysPrefs);
            editPrefs.putEntry(AdditiveMigrationState.ACK_KEY, AdditiveMigrationState.join(acknowledged));
            facade.store(editPrefs);
        }
        return computeOpenFindings();
    }

    // ---------- internals ----------

    private List<PermissionMigrationFinding> computeOpenFindings() throws RaplaException
    {
        Preferences sysPrefs = facade.getSystemPreferences();
        Set<String> worklist = AdditiveMigrationState.readWorklist(sysPrefs);
        Set<String> acknowledged = AdditiveMigrationState.readAcknowledged(sysPrefs);

        Collection<User> users = List.of(facade.getUsers());
        LocalDateTime today = facade.getOperator().getCurrentTimestamp();

        List<PermissionMigrationFinding> result = new ArrayList<>();
        for (String id : worklist)
        {
            if (acknowledged.contains(id)) continue;
            Allocatable allocatable = facade.tryResolve(new ReferenceInfo<>(id, Allocatable.class));
            if (allocatable == null) continue; // deleted since the flip → nothing to review

            List<SoftDenyAnalyzer.Finding> escalations = SoftDenyAnalyzer.findEscalations(allocatable, users, today);
            if (escalations.isEmpty()) continue; // recompute-clean (permissions were edited)

            result.add(toDto(allocatable, escalations));
        }
        return result;
    }

    private PermissionMigrationFinding toDto(Allocatable allocatable, List<SoftDenyAnalyzer.Finding> escalations) throws RaplaException
    {
        List<PrincipalEscalation> principals = new ArrayList<>();
        for (SoftDenyAnalyzer.Finding f : escalations)
        {
            String name = principalName(f);
            String current = f.currentLevel().name();
            String additive = f.additiveLevel().name();
            String explanation = f.form() == SoftDenyAnalyzer.Form.DENIED
                    ? "user " + name + " was denied and now gains " + additive
                    : "user " + name + " had only " + current + " and now gains " + additive;
            principals.add(new PrincipalEscalation(
                    f.principalType().name(), name, current, additive, f.form().name(), explanation));
        }
        return new PermissionMigrationFinding(allocatable.getId(), allocatable.getName(null), principals);
    }

    private String principalName(SoftDenyAnalyzer.Finding f)
    {
        User user = facade.tryResolve(new ReferenceInfo<>(f.principalId(), User.class));
        return user != null ? user.getUsername() : f.principalId();
    }

    private static boolean hasDeniedRow(Allocatable a)
    {
        for (Permission p : a.getPermissionList())
        {
            if (p.getAccessLevel() == AccessLevel.DENIED) return true;
        }
        return false;
    }

    private static void pruneDeniedRows(Allocatable a)
    {
        for (Permission p : new ArrayList<>(a.getPermissionList()))
        {
            if (p.getAccessLevel() == AccessLevel.DENIED) a.removePermission(p);
        }
    }

    private User requireAdmin() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        if (!user.isAdmin())
        {
            throw new RaplaSecurityException("Only admins can access the permission migration worklist");
        }
        return user;
    }

    /**
     * An authenticated non-admin → 403 (the global handler returns 401 for
     * RaplaSecurityException, which is wrong here — the actor IS authenticated,
     * just not an admin). Anonymous requests never reach this — the security
     * filter chain rejects them with 401 first. Body intentionally empty (§12 —
     * leak nothing).
     */
    @ExceptionHandler(RaplaSecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    void handleForbidden(RaplaSecurityException ex)
    {
    }
}
