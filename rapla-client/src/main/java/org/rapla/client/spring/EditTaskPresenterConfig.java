package org.rapla.client.spring;

import org.rapla.RaplaResources;
import org.rapla.client.ReservationEdit;
import org.rapla.client.event.ApplicationEventBus;
import org.rapla.client.event.TaskPresenter;
import org.rapla.client.internal.edit.EditTaskPresenter;
import org.rapla.client.internal.edit.EditTaskViewFactory;
import org.rapla.client.extensionpoints.MergeCheckExtension;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.ReservationController;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.scheduler.CommandScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

import jakarta.inject.Provider;
import java.util.Set;

/**
 * Phase 4 of PRD 002 — registers {@link EditTaskPresenter} under each of its 5
 * legacy {@code @Extension} ids so {@code Application.activityPresenters}
 * (a {@code Map<String, Provider<TaskPresenter>>} keyed by id) can dispatch to it.
 *
 * <p>The legacy DI used {@code }
 * five times on one class. Spring's {@code @Service} can only set a single bean
 * name, so we use 5 prototype-scoped {@code @Bean} factory methods, each named
 * after one of the legacy ids and each returning a freshly-constructed
 * {@code EditTaskPresenter}. {@code Provider<TaskPresenter>} from
 * {@code activityPresenters.get("editEvents").get()} resolves to a fresh instance
 * via Spring's prototype scope, matching the legacy semantics.
 */
@Configuration
public class EditTaskPresenterConfig
{
    @Bean(name = EditTaskPresenter.EDIT_EVENTS_ID)
    @Scope(value = "prototype", proxyMode = ScopedProxyMode.NO)
    @Lazy
    public EditTaskPresenter editEventsTaskPresenter(ClientFacade clientFacade, EditTaskViewFactory editTaskViewFactory,
                                                     DialogUiFactoryInterface dialogUiFactory, RaplaResources i18n,
                                                     ApplicationEventBus eventBus, CalendarSelectionModel model,
                                                     Provider<ReservationEdit> reservationEditProvider,
                                                     ReservationController reservationController, Set<MergeCheckExtension> mergeCheckers,
                                                     CommandScheduler scheduler)
    {
        return new EditTaskPresenter(clientFacade, editTaskViewFactory, dialogUiFactory, i18n, eventBus, model,
                reservationEditProvider, reservationController, mergeCheckers, scheduler);
    }

    @Bean(name = EditTaskPresenter.EDIT_RESOURCES_ID)
    @Scope(value = "prototype", proxyMode = ScopedProxyMode.NO)
    @Lazy
    public EditTaskPresenter editResourcesTaskPresenter(ClientFacade clientFacade, EditTaskViewFactory editTaskViewFactory,
                                                        DialogUiFactoryInterface dialogUiFactory, RaplaResources i18n,
                                                        ApplicationEventBus eventBus, CalendarSelectionModel model,
                                                        Provider<ReservationEdit> reservationEditProvider,
                                                        ReservationController reservationController, Set<MergeCheckExtension> mergeCheckers,
                                                        CommandScheduler scheduler)
    {
        return new EditTaskPresenter(clientFacade, editTaskViewFactory, dialogUiFactory, i18n, eventBus, model,
                reservationEditProvider, reservationController, mergeCheckers, scheduler);
    }

    @Bean(name = EditTaskPresenter.CREATE_RESERVATION_FOR_DYNAMIC_TYPE)
    @Scope(value = "prototype", proxyMode = ScopedProxyMode.NO)
    @Lazy
    public EditTaskPresenter createReservationForDynamicTypeTaskPresenter(ClientFacade clientFacade, EditTaskViewFactory editTaskViewFactory,
                                                                          DialogUiFactoryInterface dialogUiFactory, RaplaResources i18n,
                                                                          ApplicationEventBus eventBus, CalendarSelectionModel model,
                                                                          Provider<ReservationEdit> reservationEditProvider,
                                                                          ReservationController reservationController, Set<MergeCheckExtension> mergeCheckers,
                                                                          CommandScheduler scheduler)
    {
        return new EditTaskPresenter(clientFacade, editTaskViewFactory, dialogUiFactory, i18n, eventBus, model,
                reservationEditProvider, reservationController, mergeCheckers, scheduler);
    }

    @Bean(name = EditTaskPresenter.CREATE_RESERVATION_FROM_TEMPLATE)
    @Scope(value = "prototype", proxyMode = ScopedProxyMode.NO)
    @Lazy
    public EditTaskPresenter createReservationFromTemplateTaskPresenter(ClientFacade clientFacade, EditTaskViewFactory editTaskViewFactory,
                                                                        DialogUiFactoryInterface dialogUiFactory, RaplaResources i18n,
                                                                        ApplicationEventBus eventBus, CalendarSelectionModel model,
                                                                        Provider<ReservationEdit> reservationEditProvider,
                                                                        ReservationController reservationController, Set<MergeCheckExtension> mergeCheckers,
                                                                        CommandScheduler scheduler)
    {
        return new EditTaskPresenter(clientFacade, editTaskViewFactory, dialogUiFactory, i18n, eventBus, model,
                reservationEditProvider, reservationController, mergeCheckers, scheduler);
    }

    @Bean(name = EditTaskPresenter.MERGE_RESOURCES_ID)
    @Scope(value = "prototype", proxyMode = ScopedProxyMode.NO)
    @Lazy
    public EditTaskPresenter mergeResourcesTaskPresenter(ClientFacade clientFacade, EditTaskViewFactory editTaskViewFactory,
                                                         DialogUiFactoryInterface dialogUiFactory, RaplaResources i18n,
                                                         ApplicationEventBus eventBus, CalendarSelectionModel model,
                                                         Provider<ReservationEdit> reservationEditProvider,
                                                         ReservationController reservationController, Set<MergeCheckExtension> mergeCheckers,
                                                         CommandScheduler scheduler)
    {
        return new EditTaskPresenter(clientFacade, editTaskViewFactory, dialogUiFactory, i18n, eventBus, model,
                reservationEditProvider, reservationController, mergeCheckers, scheduler);
    }
}
