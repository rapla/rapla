package org.rapla.client.edit.reservation.sample;

import org.rapla.client.ActivityManager;
import org.rapla.client.ActivityPresenter;
import org.rapla.entities.Entity;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.storage.StorageOperator;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Created by Christopher on 17.09.2015.
 */
@Extension(provides = ActivityPresenter.class, id = ReservationPresenter.EDIT_ACTIVITY_ID) public class ReservationEditActivityController
        implements ActivityPresenter
{
    @Inject private Provider<ReservationPresenter> presenterProvider;
    @Inject private ClientFacade facade;
    @Inject private Logger logger;

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
                    presenterProvider.get().edit((Reservation) entity, false);
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
