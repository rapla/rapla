package org.rapla.client;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.AbstractActivityController;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.TaskPresenter;
import org.rapla.client.extensionpoints.ClientExtension;
import org.rapla.client.internal.CommandAbortedException;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Singleton
public class Application implements ApplicationView.Presenter, ModificationListener
{
    public static final String CLOSE_ACTIVITY_ID = "close";
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
    private TaskPresenter placeTaskPresenter;
    final private DialogUiFactoryInterface dialogUiFactory;
    private final Map<ApplicationEvent, TaskPresenter> openDialogsPresenter = new HashMap<>();

    @Inject
    public Application(final ApplicationView mainView, EventBus eventBus, Logger logger, BundleManager bundleManager, ClientFacade clientFacade,
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
        mainView.removeWindow(activity);
        openDialogsPresenter.remove(activity);
        return true;
    }

    public boolean startAction(ApplicationEvent activity, boolean isPlace)
    {
        final String activityId = activity.getApplicationEventId();
        if (activityId.equals(Application.CLOSE_ACTIVITY_ID))
        {
            mainView.close();
            clientFacade.removeModificationListener(this);
            try
            {
                closeCallback.run();
            }
            catch (Exception ex)
            {
                logger.error(ex.getMessage(), ex);
            }
            return false;
        }

        if (mainView.hasWindow(activity))
        {
            mainView.requestFocus(activity);
            return false;
        }

        final PopupContext popupContext = mainView.createPopupContext();//activity.getPopupContext();

        final Provider<TaskPresenter> taskPresenterProvider = activityPresenters.get(activityId);
        if (taskPresenterProvider == null)
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
        final Promise<Void> widgetPromise = objectRaplaWidget.thenAccept((widget) ->
        {
            if (isPlace)
            {
                placeTaskPresenter = taskPresenter;
                mainView.updateContent(widget);
                if (taskPresenter instanceof CalendarPlacePresenter)
                {
                    Runnable runnable = () -> ((CalendarPlacePresenter) taskPresenter).start();
                    scheduler.scheduleSynchronized(this, runnable, 300);
                }
            }
            else
            {
                Function<ApplicationEvent, Boolean> windowClosingFunction = (event) ->
                {
                    Promise<Void> promise = taskPresenter.processStop(event, widget).thenRun(() ->
                    {
                        event.setStop(true);
                        eventBus.fireEvent(event);
                    });
                    handleException(promise, popupContext);
                    return false;
                };
                String title = taskPresenter.getTitle(activity);
                mainView.openWindow(activity, popupContext, widget, title, windowClosingFunction);
                openDialogsPresenter.put(activity, taskPresenter);
            }
        });
        handleException(widgetPromise, popupContext);
        return true;
    }

    private void handleException(Promise<Void> promise, PopupContext popupContext)
    {
        promise.exceptionally(ex ->
        {
            final Throwable cause = ex.getCause();
            if (cause != null)
            {
                ex = cause;
            }
            if (!(ex instanceof CommandAbortedException))
            {
                showException(ex, popupContext);
            }
            return Promise.VOID;
        });
    }

    private void showException(Throwable ex, PopupContext popupContext)
    {
        dialogUiFactory.showException(ex, popupContext);
    }

    private void initLanguage(boolean defaultLanguageChosen) throws RaplaException
    {
        RaplaFacade facade = this.clientFacade.getRaplaFacade();
        User user = clientFacade.getUser();
        if (!defaultLanguageChosen)
        {
            facade.update(facade.getPreferences(user),
            (prefs) ->
            {
                String currentLanguage = i18n.getLang();
                prefs.putEntry(RaplaLocale.LANGUAGE_ENTRY, currentLanguage);
            }).exceptionally((ex) -> logger.error("Can't  store language change", ex));
        }
        else
        {
            final String systemDefaultLang = facade.getSystemPreferences().getEntryAsString(RaplaLocale.LANGUAGE_ENTRY, null);
            String language = facade.getPreferences(user).getEntryAsString(RaplaLocale.LANGUAGE_ENTRY, systemDefaultLang);
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

        ((FacadeImpl) clientFacade).addDirectModificationListener(new ModificationListener()
        {
            public void dataChanged(ModificationEvent evt) throws RaplaException
            {
                calendarState.dataChanged(evt);
            }
        });

        final RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
        // start client provides
        for (ClientExtension ext : clientExtensions.get())
        {
            ext.start();
        }

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
            scheduler.schedule(() ->
            {
                mainView.setStatusMessage(name, admin);
            }, 2000);

        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    protected boolean shouldExit()
    {
        try
        {
            PopupContext popupContext = mainView.createPopupContext();
            DialogInterface dlg = dialogUiFactory.create(popupContext, true, i18n.getString("exit.title"), i18n.getString("exit.question"),
                    new String[] { i18n.getString("exit.ok"), i18n.getString("exit.abort") });
            dlg.setIcon("icon.question");
            //dlg.getButton(0).setIcon(getIcon("icon.confirm"));
            dlg.getAction(0).setIcon("icon.abort");
            dlg.setDefault(1);
            dlg.start(true);
            return (dlg.getSelectedIndex() == 0);
        }
        catch (RaplaException e)
        {
            logger.error(e.getMessage(), e);
            return true;
        }

    }

    @Override
    public boolean mainClosing()
    {
        Runnable runnable = () ->
        {
            if (!shouldExit())
            {
                return;
            }
            eventBus.fireEvent(new ApplicationEvent(CLOSE_ACTIVITY_ID, "", mainView.createPopupContext(), null));
        };
        scheduler.scheduleSynchronized(this, runnable, 0);
        return false;
    }

    @Override
    public void menuClicked(String action)
    {
    }

    @Override
    public void dataChanged(ModificationEvent evt) throws RaplaException
    {
        if (evt.isSwitchTemplateMode())
        {
            User user = clientFacade.getUser();
            String message = i18n.getString("user") + " " + user.toString();
            Allocatable template = clientFacade.getTemplate();
            final boolean admin = user.isAdmin();
            if (template != null)
            {
                Locale locale = i18n.getLocale();
                message = i18n.getString("edit-templates") + " [" + template.getName(locale) + "] " + message;
            }
            mainView.setStatusMessage(message, admin);
        }
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
    }
}
