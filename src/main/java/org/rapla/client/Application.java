package org.rapla.client;

import io.reactivex.functions.Action;
import jsinterop.annotations.JsType;
import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.AbstractActivityController;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.event.TaskPresenter;
import org.rapla.client.extensionpoints.ClientExtension;
import org.rapla.client.internal.CommandAbortedException;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.util.LocaleTools;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.ResourceAnnotations;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.facade.internal.ClientFacadeImpl;
import org.rapla.facade.internal.ModifiableCalendarState;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.AbstractRaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Promise;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@JsType
@Singleton
public class Application implements ApplicationView.Presenter, ModificationListener {
    public static final String CLOSE_ACTIVITY_ID = "close";
    private final Logger logger;
    private final BundleManager bundleManager;
    private final ClientFacade clientFacade;
    private final AbstractActivityController abstractActivityController;
    private ApplicationView mainView;
    private Provider<ApplicationView> mainViewProvider;
    private final RaplaResources i18n;

    private final ApplicationEventBus eventBus;
    private final Map<String, Provider<TaskPresenter>> activityPresenters;
    final private Provider<Set<ClientExtension>> clientExtensions;
    final Provider<CalendarSelectionModel> calendarModelProvider;
    private final CommandScheduler scheduler;
    private TaskPresenter placeTaskPresenter;
    final private DialogUiFactoryInterface dialogUiFactory;
    private final Map<ApplicationEvent, TaskPresenter> openDialogsPresenter = new HashMap<>();

    @Inject
    public Application(final Provider<ApplicationView> mainViewProvider, ApplicationEventBus eventBus, Logger logger, BundleManager bundleManager, ClientFacade clientFacade,
                       AbstractActivityController abstractActivityController, RaplaResources i18n, Map<String, Provider<TaskPresenter>> activityPresenters,
                       Provider<Set<ClientExtension>> clientExtensions, Provider<CalendarSelectionModel> calendarModel, CommandScheduler scheduler,
                       DialogUiFactoryInterface dialogUiFactory) {
        this.abstractActivityController = abstractActivityController;
        this.mainViewProvider = mainViewProvider;
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
    }

    public boolean stopAction(ApplicationEvent activity) {
        final Allocatable template = clientFacade.getTemplate();
        if (template != null) {
            final Boolean annotation = (Boolean) template.getClassification().getValue(ResourceAnnotations.FIXEDTIMEANDDURATION);
            if (annotation != null && !annotation) {
                clientFacade.setTemplate(null);
            }
        }
        mainView.removeWindow(activity);
        openDialogsPresenter.remove(activity);
        return true;
    }

    public boolean startAction(ApplicationEvent activity, boolean isPlace) {
        final String activityId = activity.getApplicationEventId();
        if (activityId.equals(Application.CLOSE_ACTIVITY_ID)) {
            mainView.close();
            clientFacade.removeModificationListener(this);
            try {
                closeCallback.run();
            } catch (Exception ex) {
                logger.error(ex.getMessage(), ex);
            }
            return false;
        }

        if (mainView.hasWindow(activity)) {
            mainView.requestFocus(activity);
            return false;
        }

        final PopupContext popupContext = mainView.createPopupContext();//activity.getPopupContext();

        final Provider<TaskPresenter> taskPresenterProvider = activityPresenters.get(activityId);
        if (taskPresenterProvider == null) {
            return false;
        }
        final TaskPresenter taskPresenter = taskPresenterProvider.get();
        if (taskPresenter == null) {
            return false;
        }

        final Promise<RaplaWidget> objectRaplaWidget = taskPresenter.startActivity(activity);
        if (objectRaplaWidget == null) {
            return false;
        }
        final Promise<Void> widgetPromise = objectRaplaWidget.thenAccept((widget) ->
        {
            if (isPlace) {
                placeTaskPresenter = taskPresenter;
                mainView.updateContent(widget);
                if (taskPresenter instanceof CalendarPlacePresenter) {
                    CalendarPlacePresenter calendarPlacePresenter = (CalendarPlacePresenter) taskPresenter;
                    scheduler.delay(()->calendarPlacePresenter.start(),500);
                }
            } else {
                Function<ApplicationEvent, Boolean> windowClosingFunction = (event) ->
                {
                    Promise<Void> promise = taskPresenter.processStop(event).thenRun(() ->
                    {
                        event.setStop(true);
                        eventBus.publish(event);
                    });
                    handleException(promise, popupContext);
                    return false;
                };
                String title = taskPresenter.getTitle(activity);
                final Observable<String> busyIdleObservable = taskPresenter.getBusyIdleObservable();
                mainView.openWindow(activity, popupContext, widget, title, windowClosingFunction, busyIdleObservable);
                openDialogsPresenter.put(activity, taskPresenter);
            }
        });
        handleException(widgetPromise, popupContext);
        return true;
    }

    private void handleException(Promise<Void> promise, PopupContext popupContext) {
        promise.exceptionally(ex ->
        {
            final Throwable cause = ex.getCause();
            if (cause != null) {
                ex = cause;
            }
            if (!(ex instanceof CommandAbortedException)) {
                showException(ex, popupContext);
            }
        });
    }

    private void showException(Throwable ex, PopupContext popupContext) {
        dialogUiFactory.showException(ex, popupContext);
    }

    private void initLanguage(boolean defaultLanguageChosen) throws RaplaException {
        RaplaFacade facade = this.clientFacade.getRaplaFacade();
        User user = clientFacade.getUser();
        if (!defaultLanguageChosen) {
            facade.update(facade.getPreferences(user),
                    (prefs) ->
                    {
                        String currentLanguage = i18n.getLang();
                        prefs.putEntry(RaplaLocale.LANGUAGE_ENTRY, currentLanguage);
                    }).exceptionally((ex) -> logger.error("Can't  store language change", ex));
        } else {
            final String localeId = facade.getSystemPreferences().getEntryAsString(AbstractRaplaLocale.LOCALE, null);
            String systemDefaultLang = null;
            if ( localeId != null)
            {
                String[] parts = localeId.split("_");
                if ( parts.length > 0)
                {
                    systemDefaultLang = parts[0];
                }
            }
            String language = facade.getPreferences(user).getEntryAsString(RaplaLocale.LANGUAGE_ENTRY, systemDefaultLang);
            if (language != null) {
                bundleManager.setLanguage(language);
            }
        }
        AttributeImpl.TRUE_TRANSLATION.setName(i18n.getLang(), i18n.getString("yes"));
        AttributeImpl.FALSE_TRANSLATION.setName(i18n.getLang(), i18n.getString("no"));
    }

    private Action closeCallback;

    public void start(boolean defaultLanguageChosen, Action closeCallback) throws RaplaException {
        this.closeCallback = closeCallback;
        initLanguage(defaultLanguageChosen);

        ModifiableCalendarState calendarState = new ModifiableCalendarState(clientFacade, calendarModelProvider);

        ((ClientFacadeImpl) clientFacade).addDirectModificationListener(evt -> calendarState.dataChanged(evt));

        final RaplaFacade raplaFacade = clientFacade.getRaplaFacade();
        raplaFacade.getReservations(clientFacade.getUser(), null, null, null);
        // start client provides
        for (ClientExtension ext : clientExtensions.get()) {
            ext.start();
        }

        boolean showToolTips = raplaFacade.getPreferences(clientFacade.getUser()).getEntryAsBoolean(RaplaBuilder.SHOW_TOOLTIP_CONFIG_ENTRY, true);
        String title = raplaFacade.getSystemPreferences().getEntryAsString(AbstractRaplaLocale.TITLE, i18n.getString("rapla.title"));
        mainView = mainViewProvider.get();
        mainView.setPresenter( this);
        mainView.init(showToolTips, title);

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
        if (admin) {
            statusMessage += " " + i18n.getString("admin.login");
        }
        mainView.setStatusMessage(statusMessage, admin);
        scheduler.delay(()->mainView.setStatusMessage(name, admin),2000);
    }

    protected Promise<Boolean> shouldExit() {
        PopupContext popupContext = mainView.createPopupContext();
        DialogInterface dlg = dialogUiFactory.createTextDialog(popupContext, i18n.getString("exit.title"), i18n.getString("exit.question"),
                new String[]{i18n.getString("exit.ok"), i18n.getString("exit.abort")});
        dlg.setIcon(i18n.getIcon("icon.question"));
        //dlg.getButton(0).setIcon(getIcon("icon.confirm"));
        dlg.getAction(0).setIcon(i18n.getIcon("icon.abort"));
        dlg.setDefault(1);
        final Promise<Integer> start = dlg.start(true);
        final Promise<Boolean> result = start.thenApply((index) -> index == 0);
        return result;
    }

    @Override
    public boolean mainClosing() {
        shouldExit().thenAccept((shouldExit) -> {
                    if (shouldExit) {
                        eventBus.publish(new ApplicationEvent(CLOSE_ACTIVITY_ID, "", mainView.createPopupContext(), null));
                    }
                }
        );
        return false;
    }

    @Override
    public void menuClicked(String action) {
    }

    @Override
    public void dataChanged(ModificationEvent evt) throws RaplaException {
        if (evt.isSwitchTemplateMode()) {
            User user = clientFacade.getUser();
            String message = i18n.getString("user") + " " + user.toString();
            Allocatable template = clientFacade.getTemplate();
            final boolean admin = user.isAdmin();
            if (template != null) {
                Locale locale = i18n.getLocale();
                message = i18n.getString("edit-templates") + " [" + template.getName(locale) + "] " + message;
            }
            mainView.setStatusMessage(message, admin);
        }
        mainView.updateView(evt);
        if (placeTaskPresenter != null) {
            placeTaskPresenter.updateView(evt);
        }
        for (TaskPresenter p : openDialogsPresenter.values()) {
            p.updateView(evt);
        }
    }

    public void stop() {
        mainView.close();
    }
}
