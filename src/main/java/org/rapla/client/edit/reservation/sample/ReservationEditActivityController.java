package org.rapla.client.edit.reservation.sample;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.rapla.client.ActivityManager;
import org.rapla.client.ActivityPresenter;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.storage.StorageOperator;

@Extension(provides = ActivityPresenter.class, id = ReservationPresenter.EDIT_ACTIVITY_ID)
@Singleton
public class ReservationEditActivityController  implements ActivityPresenter
{
    final private Provider<ReservationPresenter> presenterProvider;
    final private ClientFacade facade;
    final private Logger logger;
    private final Map<String, ReservationPresenter> opendPresenter = new HashMap<>();

    @Inject
    public ReservationEditActivityController(Provider<ReservationPresenter> presenterProvider, ClientFacade facade, Logger logger)
    {
        this.presenterProvider = presenterProvider;
        this.facade = facade;
        this.logger = logger;
    }

    @Override @SuppressWarnings("rawtypes") public boolean startActivity(ActivityManager.Activity activity)
    {

        try
        {
            final StorageOperator operator = facade.getOperator();
            final Map<String, Entity> entities = operator.getFromId(Collections.singletonList(activity.getInfo()), false);
            final Collection<Entity> values = entities.values();
            for (Entity entity : values)
            {
                if (entity != null && entity instanceof Reservation)
                {
                    final Reservation reservation = (Reservation) entity;
                    final ReservationPresenter alreadyOpendPresenter = opendPresenter.get(reservation.getId());
                    if(alreadyOpendPresenter == null || !alreadyOpendPresenter.isVisible())
                    {
                        final ReservationPresenter newReservationPresenter = presenterProvider.get();
                        newReservationPresenter.edit(reservation, false);
                        opendPresenter.put(reservation.getId(), newReservationPresenter);
                    }
                    return true;
                }
            }
        }
        catch (RaplaException e)
        {
            logger.error("Error initializing activity: " + activity, e);
        }
        return false;
    }
}
