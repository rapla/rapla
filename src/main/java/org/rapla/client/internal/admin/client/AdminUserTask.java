package org.rapla.client.internal.admin.client;

import io.reactivex.functions.BiFunction;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.event.TaskPresenter;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.inject.Extension;
import org.rapla.scheduler.*;

import javax.inject.Inject;
import javax.inject.Provider;

@Extension(id = AdminUserTask.USER_ADMIN_ID, provides = TaskPresenter.class)
public class AdminUserTask implements TaskPresenter {

    public final static String USER_ADMIN_ID = "admin_user";
    final Provider<AdminUserUserGroupsView> rightsReportProvider;
    private final Subject<String> busyIdleObservable;
    private final RaplaFacade raplaFacade;
    private final DialogUiFactoryInterface dialogUIFactory;

    AdminUserUserGroupsView report;

    final RaplaResources i18n;
    final private ApplicationEventBus eventBus;


    @Inject
    public AdminUserTask(CommandScheduler scheduler, Provider<AdminUserUserGroupsView> rightsReportProvider, RaplaFacade raplaFacade, DialogUiFactoryInterface dialogUIFactory, RaplaResources i18n, ApplicationEventBus eventBus)
    {
        this.rightsReportProvider = rightsReportProvider;
        this.raplaFacade = raplaFacade;
        this.dialogUIFactory = dialogUIFactory;
        this.i18n = i18n;
        this.busyIdleObservable = scheduler.createPublisher();
        this.eventBus = eventBus;
    }
    @Override
    public <T> Promise<RaplaWidget> startActivity(ApplicationEvent applicationEvent) {
        report = rightsReportProvider.get();
        PopupContext popupContext = dialogUIFactory.createPopupContext( null);
        BiFunction<Object,Object,Promise<Void>> moveFunction = (selected, target)->
        {
            busyIdleObservable.onNext(i18n.getString("move"));
            return raplaFacade.moveCategory((Category) selected, (Category) target)
                    .exceptionally((ex) -> dialogUIFactory.showException(ex, popupContext)).finally_(() -> busyIdleObservable.onNext(""));
        };
        Runnable closeCmd = () ->
        {
            PopupContext popupEditContext = popupContext;
            ApplicationEvent event = new ApplicationEvent(applicationEvent.getApplicationEventId(), applicationEvent.getInfo(), popupEditContext, null);
            event.setStop(true);
            eventBus.publish(event);
        };
        return report.init(moveFunction, closeCmd);
    }

    @Override
    public void updateView(ModificationEvent event) {
        if ( event.isModified(Category.class) || event.isModified(User.class)) {
            report.updateView();
        }
    }

    @Override
    public String getTitle(ApplicationEvent activity) {
        final String name = i18n.getString("user") +"/"+ i18n.getString("groups") ;
        return name;
    }

    @Override
    public Observable<String> getBusyIdleObservable() {
        return busyIdleObservable;
    }

    @Override
    public Promise<Void> processStop(ApplicationEvent event) {
        return ResolvedPromise.VOID_PROMISE;
    }
}
