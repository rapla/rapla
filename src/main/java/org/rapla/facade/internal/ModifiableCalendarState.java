package org.rapla.facade.internal;

import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.internal.CalendarModelConfigurationImpl;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.CannotExistWithoutTypeException;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class ModifiableCalendarState
{
    CalendarModelConfigurationImpl beforeTemplateConf;

    private final ClientFacade facade;

    final private Provider<CalendarSelectionModel> calendarModel;

    public CalendarSelectionModel getModel()
    {
        return calendarModel.get();
    }

    public ModifiableCalendarState(ClientFacade facade,final CalendarSelectionModel model)
    {
        this.facade = facade;
        this.calendarModel = () -> {
            ((CalendarModelImpl)model).setCachingEnabled( true);
            return model;
        };
    }

    public ModifiableCalendarState(ClientFacade facade,Provider<CalendarSelectionModel> calendarModel)
    {
        this.facade = facade;
        this.calendarModel = calendarModel;
    }

    public void dataChanged(ModificationEvent evt) throws RaplaException
    {
        final CalendarModelImpl model = (CalendarModelImpl) getModel();
        model.invalidateCache();
        Collection<RaplaObject> selectedObjects = model.getSelectedObjects();
        if (evt == null)
        {
            return;
        }
        boolean switchTemplate = evt.isSwitchTemplateMode();
        if (switchTemplate)
        {
            final Allocatable template = facade.getTemplate();
            boolean changeToTemplate = template != null;
            if (changeToTemplate)
            {
                beforeTemplateConf = model.createConfiguration();
                model.setSelectedObjects(Collections.emptyList());
                model.setAllocatableFilter(null);
                model.setReservationFilter(null);
                model.setTemplateId( template.getId());
            }
            else if (beforeTemplateConf != null)
            {
                model.setConfiguration(beforeTemplateConf, null);
                beforeTemplateConf = null;
                model.setTemplateId( null );
            }

        }
        {
            Collection<RaplaObject> newSelection = new ArrayList<>();
            boolean changed = false;
            for (RaplaObject obj : selectedObjects)
            {
                if (obj instanceof Entity)
                {
                    if (!evt.isRemoved((Entity) obj))
                    {
                        newSelection.add(obj);
                    }
                    else
                    {
                        changed = true;
                    }
                }
            }
            if (changed)
            {
                model.setSelectedObjects(newSelection);
            }
        }
        {
            if (evt.isModified(DynamicType.class) || evt.isModified(Category.class) || evt.isModified(User.class))
            {
                CalendarModelConfigurationImpl config = model.createConfiguration();
                updateConfig(evt, config);
                if (beforeTemplateConf != null)
                {
                    updateConfig(evt, beforeTemplateConf);
                }
                model.setConfiguration(config, null);
            }
        }
    }

    private void updateConfig(ModificationEvent evt, CalendarModelConfigurationImpl config) throws CannotExistWithoutTypeException
    {
        final CalendarModelImpl model = (CalendarModelImpl) getModel();
        User user2 = model.getUser();
        if (user2 != null && evt.isModified(user2))
        {
            Set<User> changed = RaplaType.retainObjects(evt.getChanged(), Collections.singleton(user2));
            if (changed.size() > 0)
            {
                User newUser = changed.iterator().next();
                model.setUser(newUser);
            }
        }
        for (RaplaObject obj : evt.getChanged())
        {
            if (obj.getTypeClass() == DynamicType.class)
            {
                DynamicType type = (DynamicType) obj;
                if (config.needsChange(type))
                {
                    config.commitChange(type);
                }
            }
        }
        for (ReferenceInfo obj : evt.getRemovedReferences())
        {
            if (obj.getType().equals(DynamicType.class))
            {
                config.commitRemoveId(obj.getId());
            }
        }
    }
}
