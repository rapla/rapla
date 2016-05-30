package org.rapla.client;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.AbstractActivityController;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.TaskPresenter;
import org.rapla.client.extensionpoints.ClientExtension;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.facade.internal.ModifiableCalendarState;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Singleton public class Application implements ApplicationView.Presenter, ModificationListener
{

    private final Logger logger;
    private final BundleManager bundleManager;
    private final ClientFacade clientFacade;
    private final AbstractActivityController abstractActivityController;
    private final ApplicationView mainView;
    private final RaplaResources i18n;

    private final EventBus eventBus;
    private final Map<String, Provider<TaskPresenter>> activityPresenters;
    final private Provider<Set<ClientExtension>> clientExtensions;
    final Provider<CalendarSelectionModel> calendarModelProvider;
    private final CommandScheduler scheduler;
    //final private ModifiableCalendarState calendarState;
    private TaskPresenter placeTaskPresenter;
    final private DialogUiFactoryInterface dialogUiFactory;
    private final Map<ApplicationEvent, DialogInterface> openDialogs = new HashMap<>();
    private final Map<ApplicationEvent, TaskPresenter> openDialogsPresenter = new HashMap<>();


    @Inject public Application(final ApplicationView mainView, EventBus eventBus, Logger logger, BundleManager bundleManager, ClientFacade clientFacade,
            AbstractActivityController abstractActivityController, RaplaResources i18n, Map<String, Provider<TaskPresenter>> activityPresenters,
            Provider<Set<ClientExtension>> clientExtensions, Provider<CalendarSelectionModel> calendarModel, CommandScheduler scheduler,
            DialogUiFactoryInterface dialogUiFactory)
    {
        this.mainView = mainView;
        this.abstractActivityController = abstractActivityController;
        this.bundleManager = bundleManager;
        this.clientFacade = clientFacade;
        this.logger = logger;
        this.eventBus = eventBus;
        this.i18n = i18n;
        this.activityPresenters = activityPresenters;
        this.clientExtensions = clientExtensions;
        this.calendarModelProvider = calendarModel;
        this.scheduler = scheduler;
        this.dialogUiFactory = dialogUiFactory;
        mainView.setPresenter(this);
    }

    public boolean stopAction(ApplicationEvent activity)
    {
        final String activityId = activity.getApplicationEventId();
        final PopupContext popupContext =  mainView.createPopupContext();//activity.getPopupContext();
        final DialogInterface dialogInterface = openDialogs.remove(activity);
        if (dialogInterface != null)
        {
            dialogInterface.close();
        }
        openDialogsPresenter.remove(activity);
        return true;
    }
    public boolean startAction(ApplicationEvent activity, boolean isPlace)
    {
        final DialogInterface dialogInterface = openDialogs.get(activity);
        if(dialogInterface != null)
        {
            dialogInterface.toFront();
            dialogInterface.requestFocus();
            return false;
        }
        final String activityId = activity.getApplicationEventId();
        final PopupContext popupContext =  mainView.createPopupContext();//activity.getPopupContext();

        final Provider<TaskPresenter> taskPresenterProvider = activityPresenters.get(activityId);
        if ( taskPresenterProvider == null)
        {
            return false;
        }
        final TaskPresenter taskPresenter = taskPresenterProvider.get();
        if (taskPresenter == null)
        {
            return false;
        }
        final Promise<RaplaWidget> objectRaplaWidget = taskPresenter.startActivity(activity);
        if (objectRaplaWidget == null)
        {
            return false;
        }
        objectRaplaWidget.thenAccept((widget) -> {
            if (isPlace)
            {
                placeTaskPresenter = taskPresenter;
                mainView.updateContent(widget);
            }
            else
            {
                boolean modal = false;
                String[] options = new String[] {};
                final DialogInterface dialog = dialogUiFactory.create(popupContext, modal, widget.getComponent(), options);
                dialog.setSize(1050, 700);
                openDialogs.put(activity, dialog);
                openDialogsPresenter.put(activity, taskPresenter);
                dialog.start(false);
            }
        }).exceptionally(ex -> {
            showException(ex,popupContext );
            return Promise.VOID;
        });
        return true;
    }

    private void showException(Throwable ex, PopupContext popupContext)
    {
        dialogUiFactory.showException(ex, popupContext);
    }

    private void initLanguage(boolean defaultLanguageChosen) throws RaplaException
    {
        RaplaFacade facade = this.clientFacade.getRaplaFacade();
        if (!defaultLanguageChosen)
        {
            Preferences prefs = facade.edit(facade.getSystemPreferences());
            String currentLanguage = i18n.getLang();
            prefs.putEntry(RaplaLocale.LANGUAGE_ENTRY, currentLanguage);
            try
            {
                facade.store(prefs);
            }
            catch (Exception e)
            {
                logger.error("Can't  store language change", e);
            }
        }
        else
        {
            String language = facade.getSystemPreferences().getEntryAsString(RaplaLocale.LANGUAGE_ENTRY, null);
            if (language != null)
            {
                BundleManager localeSelector = (BundleManager) bundleManager;
                localeSelector.setLanguage(language);
            }
        }
        AttributeImpl.TRUE_TRANSLATION.setName(i18n.getLang(), i18n.getString("yes"));
        AttributeImpl.FALSE_TRANSLATION.setName(i18n.getLang(), i18n.getString("no"));
    }

    private Runnable closeCallback;

    public void start(boolean defaultLanguageChosen, Runnable closeCallback) throws RaplaException
    {
        this.closeCallback = closeCallback;
        initLanguage(defaultLanguageChosen);

        ModifiableCalendarState calendarState = new ModifiableCalendarState(clientFacade, calendarModelProvider);
        //        StorageOperator operator = facade.getOperator();

        ((FacadeImpl) clientFacade).addDirectModificationListener(new ModificationListener()
        {

            public void dataChanged(ModificationEvent evt) throws RaplaException
            {
                calendarState.dataChanged(evt);
            }
        });
        //        if ( facade.isClientForServer() )
        //        {
        //            addContainerProvidedComponent (RaplaClientExtensionPoints.SYSTEM_OPTION_PANEL_EXTENSION , ConnectionOption.class);
        //        }

        final RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
        //Preferences systemPreferences = raplaFacade.getSystemPreferences();
        //List<PluginDescriptor<ClientServiceContainer>> pluginList = initializePlugins(systemPreferences, ClientServiceContainer.class);
        //addContainerProvidedComponentInstance(ClientServiceContainer.CLIENT_PLUGIN_LIST, pluginList);

        // start client provides
        for (ClientExtension ext : clientExtensions.get())
        {
            ext.start();
        }

        //FIXME add customizable DateRenderer
        // Add daterender if not provided by the plugins
        //        if ( !getContext().has( DateRenderer.class))
        //        {
        //            addContainerProvidedComponent(DateRenderer.class, RaplaDateRenderer.class);
        //        }
        boolean showToolTips = raplaFacade.getPreferences(clientFacade.getUser()).getEntryAsBoolean(RaplaBuilder.SHOW_TOOLTIP_CONFIG_ENTRY, true);
        String title = raplaFacade.getSystemPreferences().getEntryAsString(AbstractRaplaLocale.TITLE, i18n.getString("rapla.title"));
        mainView.init(showToolTips, title);

        try
        {
            AbstractActivityController am = abstractActivityController;
            am.setApplication(this);
            am.init();

            User user = clientFacade.getUser();
            final boolean admin = user.isAdmin();
            String message = i18n.getString("user") + " " + user.toString();
            Allocatable template = clientFacade.getTemplate();
            if (template != null)
            {
                Locale locale = i18n.getLocale();
                message = i18n.getString("edit-templates") + " [" + template.getName(locale) + "] " + message;
            }
            mainView.setStatusMessage(message, user.isAdmin());
            mainView.updateMenu();
            // Test for the resources
            clientFacade.addModificationListener(this);
            final String name = user.getName() == null || user.getName().isEmpty() ? user.getUsername() : user.getName();
            String statusMessage = i18n.format("rapla.welcome", name);
            if (admin)
            {
                statusMessage += " " + i18n.getString("admin.login");
            }

            mainView.setStatusMessage(statusMessage, admin);
            scheduler.schedule(() -> {
                mainView.setStatusMessage(name, false);
            }, 2000);

        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public void mainClosing()
    {
        clientFacade.removeModificationListener(this);
        try
        {

            closeCallback.run();

        }
        catch (Exception ex)
        {
            logger.error(ex.getMessage(), ex);
        }
    }

    @Override public void menuClicked(String action)
    {
    }

    @Override public void dataChanged(ModificationEvent evt) throws RaplaException
    {
        mainView.updateView(evt);
        placeTaskPresenter.updateView(evt);
        for (TaskPresenter p : openDialogsPresenter.values())
        {
            p.updateView(evt);
        }
    }

    public void stop()
    {
        mainView.close();
        final Collection<DialogInterface> openDialogs = this.openDialogs.values();
        for (DialogInterface di : openDialogs)
        {
            di.close();
        }
    }
}
