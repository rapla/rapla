package org.rapla.server.spring.web;

import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.server.internal.RemoteStorageImpl;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.entities.domain.internal.ReservationImpl;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.dbrm.AppointmentMap;
import org.rapla.storage.dbrm.RemoteStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import java.time.LocalDateTime;
/**
 * REST endpoints for the {@code /storage/*} family — the bulk-fetch / dispatch / refresh
 * surface the Swing client uses for batched operations. After PRD 008 phases 5-7 + this turn,
 * the controller calls direct sync methods on {@link RemoteStorageImpl}; the {@link RemoteStorage}
 * Promise-returning interface stays for the Swing client over HTTP. No more {@code .waitFor}
 * blocking helper — every method here is straight-through sync.
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
@RequestMapping(value = "/storage", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class RemoteStorageController
{
    private final RemoteStorageImpl delegate;

    public RemoteStorageController(RemoteStorageImpl delegate)
    {
        this.delegate = delegate;
    }

    // ---- account / password ------------------------------------------------

    @GetMapping("/change/canchangepassword")
    public boolean canChangePassword() throws RaplaException
    {
        return delegate.canChangePassword();
    }

    @PostMapping("/change/password")
    public void changePassword(@RequestBody RemoteStorage.PasswordPost job) throws RaplaException
    {
        delegate.changePassword(job);
    }

    @PostMapping("/change/name")
    public void changeName(@RequestParam(value = "username", required = false) String username,
                           @RequestParam(value = "title",    required = false) String newTitle,
                           @RequestParam(value = "surename", required = false) String newSurename,
                           @RequestBody String newLastname) throws RaplaException
    {
        delegate.changeName(username, newTitle, newSurename, newLastname);
    }

    @PostMapping("/change/email")
    public void changeEmail(@RequestParam(value = "username", required = false) String username,
                            @RequestBody String newEmail) throws RaplaException
    {
        delegate.changeEmail(username, newEmail);
    }

    @PostMapping("/confirm/email")
    public void confirmEmail(@RequestParam(value = "username", required = false) String username,
                             @RequestBody String newEmail) throws RaplaException
    {
        delegate.confirmEmail(username, newEmail);
    }

    // ---- bulk fetch / sync -------------------------------------------------

    @GetMapping("/resources")
    public UpdateEvent getResources() throws RaplaException
    {
        return delegate.getResources();
    }

    @PostMapping("/queryAppointments")
    public AppointmentMap queryAppointments(@RequestBody RemoteStorage.QueryAppointments job) throws RaplaException
    {
        return delegate.queryAppointments(job);
    }

    @PostMapping("/entity/recursiveSync")
    public UpdateEvent getEntityRecursive(
            @RequestParam(value = "errorIfNotFound", required = false) Boolean errorIfNotFound,
            @RequestBody UpdateEvent.SerializableReferenceInfo[] infos) throws RaplaException
    {
        return delegate.getEntityRecursive(errorIfNotFound, infos);
    }

    @PostMapping("/entity/dependent")
    public UpdateEvent getEntityDependencies(
            @RequestParam(value = "errorIfNotFound", required = false) Boolean errorIfNotFound,
            @RequestBody UpdateEvent.SerializableReferenceInfo[] infos) throws RaplaException
    {
        return delegate.getEntityDependencies(errorIfNotFound, infos);
    }

    @PostMapping("/refreshAllEvents")
    public UpdateEvent refreshAllEvents(@RequestParam(value = "lastValidated") String lastSyncedTime) throws RaplaException
    {
        return delegate.refreshAllEvents(lastSyncedTime);
    }

    @PostMapping("/refresh")
    public UpdateEvent refresh(@RequestParam(value = "lastValidated") String lastValidated) throws RaplaException
    {
        return delegate.refresh(lastValidated);
    }

    // ---- writes ------------------------------------------------------------

    @PostMapping("/dispatch")
    public UpdateEvent dispatch(@RequestBody UpdateEvent event) throws RaplaException
    {
        return delegate.dispatch(event);
    }

    // ---- ID allocation -----------------------------------------------------

    @PostMapping("/identifier")
    public List<String> createIdentifier(
            @RequestParam(value = "raplaType", required = false) String raplaType,
            @RequestParam(value = "count",     required = false) int count) throws RaplaException
    {
        return delegate.createIdentifier(raplaType, count);
    }

    // ---- conflicts / bindings ---------------------------------------------

    @GetMapping("/conflicts")
    public List<ConflictImpl> getConflicts() throws RaplaException
    {
        return delegate.getConflicts();
    }

    @PostMapping("/allocatable/bindings/first")
    public RemoteStorage.BindingMap getFirstAllocatableBindings(@RequestBody RemoteStorage.AllocatableBindingsRequest job) throws RaplaException
    {
        return delegate.getFirstAllocatableBindings(job);
    }

    @PostMapping("/allocatable/bindings/all")
    public List<ReservationImpl> getAllAllocatableBindings(@RequestBody RemoteStorage.AllocatableBindingsRequest job) throws RaplaException
    {
        return delegate.getAllAllocatableBindings(job);
    }

    @PostMapping("/allocatable/date/next")
    public LocalDateTime getNextAllocatableDate(@RequestBody RemoteStorage.NextAllocatableDateRequest job) throws RaplaException
    {
        return delegate.getNextAllocatableDate(job);
    }

    // ---- user --------------------------------------------------------------

    @GetMapping("/user")
    public String getUsername(@RequestParam(value = "userId") String userId) throws RaplaException
    {
        return delegate.getUsername(userId);
    }

    // ---- merge / lifecycle -------------------------------------------------

    @PostMapping("/merge")
    public UpdateEvent doMerge(@RequestBody RemoteStorage.MergeRequest job,
                                @RequestParam(value = "lastSynched", required = false) String lastSyncedTime) throws RaplaException
    {
        return delegate.doMerge(job, lastSyncedTime);
    }

    @PostMapping("/restart")
    public void restartServer() throws RaplaException
    {
        delegate.restartServer();
    }
}
