package org.rapla.client;

import javax.inject.Inject;

import org.rapla.client.event.DetailEndEvent;
import org.rapla.client.event.DetailEndEvent.DetailEndEventHandler;
import org.rapla.client.event.DetailSelectEvent;
import org.rapla.client.event.DetailSelectEvent.DetailSelectEventHandler;
import org.rapla.framework.RaplaException;

import com.google.web.bindery.event.shared.EventBus;

public abstract class ActivityManager implements DetailSelectEventHandler,
		DetailEndEventHandler {

	private final Application application;

	@Inject
	public ActivityManager(Application application, EventBus eventBus) {
		this.application = application;
		eventBus.addHandler(DetailSelectEvent.TYPE, this);
		eventBus.addHandler(DetailEndEvent.TYPE, this);
	}

	@Override
	public void detailsRequested(DetailSelectEvent event) {
		createActivityOrPlace(event);
		application.detailsRequested(event);
	}

	public abstract void init() throws RaplaException;

	protected abstract void createActivityOrPlace(DetailSelectEvent event);
}
