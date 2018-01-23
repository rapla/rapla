/*--------------------------------------------------------------------------*
 | Copyright (C) 2013 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.plugin.tempatewizard.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.extensionpoints.ReservationWizardExtension;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.MenuScroller;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.tempatewizard.TemplatePlugin;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import javax.swing.MenuElement;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/** This ReservationWizard displays no wizard and directly opens a ReservationEdit Window
 */
@Extension(provides = ReservationWizardExtension.class, id = TemplatePlugin.PLUGIN_ID) public class TemplateWizard
        implements ReservationWizardExtension, ModificationListener
{
    final public static TypedComponentRole<Boolean> ENABLED = new TypedComponentRole<Boolean>("org.rapla.plugin.templatewizard.enabled");
    boolean templateNamesValid = false;
    Collection<Allocatable> templateNames;
    private final CalendarModel model;
    private final RaplaImages raplaImages;
    private final PermissionController permissionController;
    private final ApplicationEventBus eventBus;
    protected final ClientFacade clientFacade;
    protected final RaplaFacade raplaFacade;
    protected final RaplaLocale raplaLocale;
    protected final Logger logger;
    protected final RaplaResources i18n;
    protected final DialogUiFactoryInterface dialogUiFactory;

    @Inject public TemplateWizard(ClientFacade clientFacade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel model,
                                  RaplaImages raplaImages, ApplicationEventBus eventBus, DialogUiFactoryInterface dialogUiFactory) throws RaplaInitializationException
    {
        this.logger = logger;
        this.i18n = i18n;
        this.clientFacade = clientFacade;
        this.raplaFacade = clientFacade.getRaplaFacade();
        this.raplaLocale = raplaLocale;
        this.model = model;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.permissionController = raplaFacade.getPermissionController();
        this.eventBus = eventBus;
        clientFacade.addModificationListener(this);

    }

    public String getId()
    {
        return "020_templateWizard";
    }

    @Override public void dataChanged(ModificationEvent evt) throws RaplaException
    {
        if (evt.getInvalidateInterval() != null)
        {
            templateNamesValid =false;
        }
    }

    @Override public boolean isEnabled()
    {
        try
        {
            return raplaFacade.getSystemPreferences().getEntryAsBoolean(ENABLED, true);
        }
        catch (RaplaException e)
        {
            return false;
        }
    }

    private Collection<Allocatable> updateTemplateNames() throws RaplaException
    {
        if ( templateNamesValid)
        {
            return templateNames;
        }
        List<Allocatable> templates = new ArrayList<Allocatable>();
        for (Allocatable template : raplaFacade.getTemplates())
        {
            templates.add(template);
        }
        templateNames = templates;
        templateNamesValid = true;
        return templates;
    }

    public MenuElement getMenuElement()
    {
        //final RaplaFacade clientFacade = getClientFacade();
        User user;
        Collection<Allocatable> templateNames;
        try
        {
            user = clientFacade.getUser();
            templateNames = updateTemplateNames();
        }
        catch (RaplaException e)
        {
            logger.error("Error creating menu element for TemplateWizard: "+e.getMessage(), e);
            return null;
        }
        boolean canCreateReservation = permissionController.canCreateReservation(user);

        MenuElement element;
        if (templateNames.size() == 0)
        {
            return null;
        }
        if (templateNames.size() == 1)
        {
            Allocatable template = templateNames.iterator().next();
            RaplaMenuItem item = new RaplaMenuItem(getId());
            item.setEnabled(raplaFacade.canAllocate(model, user) && canCreateReservation);
            final String templateName = template.getName(getLocale());
            item.setText(getSingleTemplateName(templateName));
            item.setIcon(raplaImages.getIconFromKey("icon.new"));
            item.addActionListener((evt)->   createWithTemplate(template)) ;
            element = item;
        }
        else
        {
            RaplaMenu item = new RaplaMenu(getId());
            item.setEnabled(raplaFacade.canAllocate(model, user) && canCreateReservation);
            item.setText(getMultipleTemplateName());
            item.setIcon(raplaImages.getIconFromKey("icon.new"));
            @SuppressWarnings("unchecked") Comparator<String> collator = (Comparator<String>) (Comparator) Collator.getInstance(raplaLocale.getLocale());
            Map<String, Collection<Allocatable>> templateMap = new HashMap<String, Collection<Allocatable>>();

            Set<String> templateSet = new TreeSet<String>(collator);
            Locale locale = getLocale();
            for (Allocatable template : templateNames)
            {
                String name = template.getName(locale);
                templateSet.add(name);
                Collection<Allocatable> collection = templateMap.get(name);
                if (collection == null)
                {
                    collection = new ArrayList<Allocatable>();
                    templateMap.put(name, collection);
                }
                collection.add(template);
            }

            SortedMap<String, Set<String>> keyGroup = new TreeMap<String, Set<String>>(collator);
            if (templateSet.size() > 10)
            {
                for (String string : templateSet)
                {
                    if (string.length() == 0)
                    {
                        continue;
                    }
                    String firstChar = string.substring(0, 1);
                    Set<String> group = keyGroup.get(firstChar);
                    if (group == null)
                    {
                        group = new TreeSet<String>(collator);
                        keyGroup.put(firstChar, group);
                    }
                    group.add(string);
                }

                SortedMap<String, Set<String>> merged = merge(keyGroup, collator);
                for (String subMenuName : merged.keySet())
                {
                    RaplaMenu subMenu = new RaplaMenu(getId());
                    item.setIcon(raplaImages.getIconFromKey("icon.new"));
                    subMenu.setText(subMenuName);
                    Set<String> set = merged.get(subMenuName);
                    int maxItems = 20;
                    if (set.size() >= maxItems)
                    {
                        int millisToScroll = 40;
                        MenuScroller.setScrollerFor(subMenu, maxItems, millisToScroll);
                    }
                    addTemplates(subMenu, set, templateMap);
                    item.add(subMenu);
                }
            }
            else
            {
                addTemplates(item, templateSet, templateMap);
            }
            element = item;
        }
        return element;
    }

    protected String getMultipleTemplateName()
    {
        return i18n.getString("new_reservations_from_template");
    }

    protected String getSingleTemplateName(String templateName)
    {
        return i18n.format("new_reservation.format", templateName);
    }

    public void addTemplates(RaplaMenu item, Set<String> templateSet, Map<String, Collection<Allocatable>> templateMap)
    {
        Locale locale = getLocale();

        for (String templateName : templateSet)
        {
            Collection<Allocatable> collection = templateMap.get(templateName);
            // there could be multiple templates with the same name
            for (final Allocatable template : collection)
            {
                RaplaMenuItem newItem = new RaplaMenuItem(template.getName(locale));
                newItem.setText(templateName);
                newItem.addActionListener((evt)->createWithTemplate(template));
                item.add(newItem);

            }
        }
    }

    private SortedMap<String, Set<String>> merge(SortedMap<String, Set<String>> keyGroup, Comparator<String> comparator)
    {
        SortedMap<String, Set<String>> result = new TreeMap<String, Set<String>>(comparator);
        String beginnChar = null;
        String currentChar = null;
        Set<String> currentSet = null;
        for (String key : keyGroup.keySet())
        {
            Set<String> set = keyGroup.get(key);
            if (currentSet == null)
            {
                currentSet = new TreeSet<String>(comparator);
                beginnChar = key;
                currentChar = key;
            }
            if (!key.equals(currentChar))
            {
                if (set.size() + currentSet.size() > 10)
                {
                    String storeKey;
                    if (beginnChar != null && !beginnChar.equals(currentChar))
                    {
                        storeKey = beginnChar + "-" + currentChar;
                    }
                    else
                    {
                        storeKey = currentChar;
                    }
                    result.put(storeKey, currentSet);
                    currentSet = new TreeSet<String>(comparator);
                    beginnChar = key;
                    currentChar = key;
                }
                else
                {
                    currentChar = key;
                }
            }
            currentSet.addAll(set);
        }
        String storeKey;
        if (beginnChar != null)
        {
            if (!beginnChar.equals(currentChar))
            {
                storeKey = beginnChar + "-" + currentChar;
            }
            else
            {
                storeKey = currentChar;
            }
            result.put(storeKey, currentSet);
        }
        return result;
    }

    protected Locale getLocale()
    {
        return raplaLocale.getLocale();
    }

    protected void createWithTemplate( Allocatable template)
    {
        final String id = template.getId();
        PopupContext popupContext = dialogUiFactory.createPopupContext( null);
        ApplicationEventContext context = null;
        eventBus.publish(new ApplicationEvent(EditTaskPresenter.CREATE_RESERVATION_FROM_TEMPLATE, id, popupContext, context));
    }

}




