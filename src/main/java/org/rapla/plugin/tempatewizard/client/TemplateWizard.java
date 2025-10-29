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
package org.rapla.plugin.tempatewizard.client;

import io.reactivex.rxjava3.functions.Consumer;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.extensionpoints.ReservationWizardExtension;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.client.menu.MenuInterface;
import org.rapla.client.menu.MenuItemFactory;
import org.rapla.components.util.treemenubalance.BalancedHierarchicalMenu;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.tempatewizard.TemplatePlugin;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/** This ReservationWizard displays no wizard and directly opens a ReservationEdit Window
 */
@Extension(provides = ReservationWizardExtension.class, id = TemplatePlugin.PLUGIN_ID) public class TemplateWizard
        implements ReservationWizardExtension, ModificationListener
{
    final public static TypedComponentRole<Boolean> ENABLED = new TypedComponentRole<>("org.rapla.plugin.templatewizard.enabled");
    boolean templateNamesValid = false;
    List<Allocatable> templateNames;
    private final CalendarModel model;
    private final PermissionController permissionController;
    private final ApplicationEventBus eventBus;
    protected final ClientFacade clientFacade;
    protected final RaplaFacade raplaFacade;
    protected final RaplaLocale raplaLocale;
    protected final Logger logger;
    protected final RaplaResources i18n;
    protected final DialogUiFactoryInterface dialogUiFactory;
    protected final MenuItemFactory menuItemFactory;

    @Inject public TemplateWizard(ClientFacade clientFacade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CalendarModel model,
                                  ApplicationEventBus eventBus, DialogUiFactoryInterface dialogUiFactory, MenuItemFactory menuItemFactory) throws RaplaInitializationException
    {
        this.logger = logger;
        this.i18n = i18n;
        this.clientFacade = clientFacade;
        this.raplaFacade = clientFacade.getRaplaFacade();
        this.raplaLocale = raplaLocale;
        this.model = model;
        this.dialogUiFactory = dialogUiFactory;
        this.menuItemFactory = menuItemFactory;
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

    public void setEnabled( boolean b) {
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

    private List<Allocatable> updateTemplateNames() throws RaplaException
    {
        if ( templateNamesValid)
        {
            return templateNames;
        }
        List<Allocatable> templates = new ArrayList<>();
        for (Allocatable template : raplaFacade.getTemplates())
        {
            templates.add(template);
        }
        templateNames = templates;
        templateNamesValid = true;
        return templates;
    }

    @Override
    public Object getComponent()
    {
        //final RaplaFacade clientFacade = getClientFacade();
        User user;
        List<Allocatable> templateNames;
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

        Object element;
        if (templateNames.size() == 0)
        {
            return null;
        }
        final boolean enabled = raplaFacade.canAllocate(model, user) && canCreateReservation;
        if (templateNames.size() == 1)
        {
            Allocatable template = templateNames.iterator().next();
            final String templateName = template.getName(getLocale());
            final String singleTemplateName = getSingleTemplateName(templateName);
            Consumer<PopupContext> action = enabled ? (popupContext)->createWithTemplate( template) : null;
            IdentifiableMenuEntry item = menuItemFactory.createMenuItem(singleTemplateName, i18n.getIcon("icon.new"), action);
            element = item.getComponent();
        }
        else
        {
            final String multipleTemplateName = getMultipleTemplateName();

            MenuInterface container = menuItemFactory.createMenu(multipleTemplateName, i18n.getIcon("icon.new"), "new");
            if ( enabled) {
                container.setInitializer(() -> {
                    try {
                        addTemplates(templateNames, container);

                    } catch (Exception ex) {
                        logger.error("Error initializing menu", ex);
                    }
                });

            }
            element = container.getComponent();
        }
        return element;
    }

    private void addTemplates(List<Allocatable> templateNames, MenuInterface container) {
        Locale locale1 = raplaLocale.getLocale();
        Function<Allocatable,String> namer = t -> t.getName(locale1);
        BalancedHierarchicalMenu.MenuNode<Allocatable> root = BalancedHierarchicalMenu.build(templateNames, namer,locale1,25);
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        BalancedHierarchicalMenu.print(writer,root);
        String string1 = out.toString();
        addTemplates(container,root);
    }

    private void addTemplates(MenuInterface container, BalancedHierarchicalMenu.MenuNode<Allocatable> root) {
        if (root.payload != null) {
            Allocatable template = (Allocatable) root.payload;
            final String name = template.getName(getLocale());
            Consumer<PopupContext> action = (popupContext) -> createWithTemplate(template);
            IdentifiableMenuEntry newItem = menuItemFactory.createMenuItem(name, null, action);
            container.addMenuItem(newItem);
        } else {
            for (BalancedHierarchicalMenu.MenuNode<Allocatable> child : root.children) {
                final String subMenuName = child.label;
                final String id = getId();
                if (child.payload == null ) {
                    MenuInterface subMenu = menuItemFactory.createMenu(subMenuName, null, id);
                    addTemplates(subMenu, child);
                    container.addMenuItem(subMenu);
                } else {
                    addTemplates(container, child);
                }

            }
        }
    }

    protected String getMultipleTemplateName()
    {
        return i18n.getString("new_reservations_from_template");
    }

    protected String getSingleTemplateName(String templateName)
    {
        return i18n.format("new_reservation.format", templateName);
    }

    public void addTemplates(MenuInterface container, Set<String> templateSet, Map<String, Collection<Allocatable>> templateMap)
    {
        Locale locale = getLocale();

        for (String templateName : templateSet)
        {
            Collection<Allocatable> collection = templateMap.get(templateName);
            // there could be multiple templates with the same name
            for (final Allocatable template : collection)
            {
                final String name = template.getName(locale);
                Consumer<PopupContext> action = (popupContext)->createWithTemplate(template);
                IdentifiableMenuEntry newItem = menuItemFactory.createMenuItem(templateName, null, action);
                container.addMenuItem(newItem);

            }
        }
    }

    private SortedMap<String, Set<String>> merge(SortedMap<String, Set<String>> keyGroup, Comparator<String> comparator)
    {
        SortedMap<String, Set<String>> result = new TreeMap<>(comparator);
        String beginnChar = null;
        String currentChar = null;
        Set<String> currentSet = null;
        for (String key : keyGroup.keySet())
        {
            Set<String> set = keyGroup.get(key);
            if (currentSet == null)
            {
                currentSet = new TreeSet<>(comparator);
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
                    currentSet = new TreeSet<>(comparator);
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




