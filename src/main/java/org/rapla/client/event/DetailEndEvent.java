package org.rapla.client.event;

import org.rapla.client.event.DetailEndEvent.DetailEndEventHandler;
import org.rapla.entities.Entity;

import com.google.web.bindery.event.shared.Event;

public class DetailEndEvent extends Event<DetailEndEventHandler> {
	public static interface DetailEndEventHandler {
		void detailsEnded(DetailEndEvent event);
	}

	public static final Type<DetailEndEventHandler> TYPE = new Type<DetailEndEvent.DetailEndEventHandler>();

	private final Entity<?> entity;

	public DetailEndEvent(Entity<?> entity) {
		this.entity = entity;
	}

	@Override
	public Type<DetailEndEventHandler> getAssociatedType() {
		return TYPE;
	}

	@Override
	protected void dispatch(DetailEndEventHandler handler) {
		handler.detailsEnded(this);
	}

	public Entity<?> getEntity() {
		return entity;
	}
}
