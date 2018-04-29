package org.rapla.client.internal;

import io.reactivex.functions.BiFunction;
import org.rapla.RaplaResources;
import org.rapla.client.CalendarContainer;
import org.rapla.client.EditApplicationEventContext;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.event.TaskPresenter;
import org.rapla.client.internal.admin.client.TypeCategoryView;
import org.rapla.entities.Category;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.scheduler.Subject;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;

@Extension(id = ResourceCalendarTask.ID, provides = TaskPresenter.class)
public class ResourceCalendarTask implements TaskPresenter {

    public final static String ID = "resource_calendar";

    final Provider<CalendarContainer> viewProvider;
    private final Subject<String> busyIdleObservable;
    private final DialogUiFactoryInterface dialogUIFactory;

    CalendarContainer adminView;

    final RaplaResources i18n;


    @Inject
    public ResourceCalendarTask(CommandScheduler scheduler, Provider<CalendarContainer> viewProvider,  DialogUiFactoryInterface dialogUIFactory, RaplaResources i18n)
    {
        this.viewProvider = viewProvider;
        this.dialogUIFactory = dialogUIFactory;
        this.i18n = i18n;
        this.busyIdleObservable = scheduler.createPublisher();
    }
    @Override
    public <T> Promise<RaplaWidget> startActivity(ApplicationEvent applicationEvent) {
        adminView = viewProvider.get();
        boolean editable = false;
        final EditApplicationEventContext context = (EditApplicationEventContext) applicationEvent.getContext();
        PresenterChangeCallback callback =()->{};
        try
        {
            final CalendarSelectionModel calendarModel = context.getCalendarModel();
            adminView.init(editable,calendarModel,callback);
            adminView.update( null);
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<>(e);
        }
        return new ResolvedPromise<>(adminView.provideContent());
    }

    @Override
    public void updateView(ModificationEvent event) {
        try
        {
            adminView.update(event);
        }
        catch (RaplaException ex)
        {
            final PopupContext popupContext = dialogUIFactory.createPopupContext(adminView.provideContent());
            dialogUIFactory.showException(ex, popupContext);
        }

    }

    @Override
    public String getTitle(ApplicationEvent activity) {
        final String name = "Calendar";
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
