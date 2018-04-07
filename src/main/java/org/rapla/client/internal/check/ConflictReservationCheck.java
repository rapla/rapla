package org.rapla.client.internal.check;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.entities.User;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.Conflict;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.CalendarOptionsImpl;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Extension(provides = EventCheck.class, id = "conflictcheck")
public class ConflictReservationCheck implements EventCheck
{

    private final PermissionController permissionController;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final ConflictDialogView conflictDialogView;
    ClientFacade clientFacade;
    RaplaFacade raplaFacade;
    RaplaResources i18n;

    @Inject
    public ConflictReservationCheck(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, DialogUiFactoryInterface dialogUiFactory,
            ConflictDialogView conflictDialogView)
    {
        this.clientFacade = facade;
        this.i18n = i18n;
        this.raplaFacade = facade.getRaplaFacade();
        this.conflictDialogView = conflictDialogView;
        this.permissionController = facade.getRaplaFacade().getPermissionController();
        this.dialogUiFactory = dialogUiFactory;
    }

    private boolean canCreateConflicts(Collection<Conflict> conflicts, final User user)
    {
        return conflicts.stream().flatMap(conflict -> Stream.of(conflict.getAllocatable())).noneMatch( allocatable->!permissionController.canCreateConflicts(allocatable,user));
    }

    public Promise<Boolean> check(Collection<Reservation> reservations, PopupContext sourceComponent)
    {
        final List<Conflict> conflictList = new ArrayList<>();
        Promise<Void> p = null;
        for (Reservation reservation : reservations)
        {
            Promise<Collection<Conflict>> promise = raplaFacade.getConflictsForReservation(reservation);
            final Promise<Void> resultPromise = promise.thenAccept((conflicts) -> {
                for (Conflict conflict : conflicts)
                {
                    conflictList.add(conflict);
                }
            });
            if (p == null)
            {
                p = resultPromise;
            }
            else
            {
                p = p.thenCombine(resultPromise, (a, b) -> Promise.VOID);
            }
        }
        return p.thenCompose((dummy)->
        {
            if ( conflictList.isEmpty())
            {
                return new ResolvedPromise(true);
            }
            final User user = clientFacade.getUser();
            boolean showWarning = raplaFacade.getPreferences(user).getEntryAsBoolean(CalendarOptionsImpl.SHOW_CONFLICT_WARNING, true);
            if (!showWarning && canCreateConflicts(conflictList, user))
            {
                return new ResolvedPromise(true);
            }
            Object content = conflictDialogView.getConflictPanel(conflictList);
            DialogInterface dialog = dialogUiFactory
                    .createContentDialog(sourceComponent, content, new String[] { i18n.getString("continue"), i18n.getString("back") });
            dialog.setDefault(1);
            dialog.setIcon(i18n.getIcon("icon.big_folder_conflicts"));
            dialog.getAction(0).setIcon(i18n.getIcon("icon.save"));
            dialog.getAction(1).setIcon(i18n.getIcon("icon.cancel"));
            dialog.setTitle(i18n.getString("warning.conflict"));
            return dialog.start(true).thenApply((index) -> index == 0);
        });
    }
}

