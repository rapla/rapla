package org.rapla.client.event;

import org.rapla.client.event.AddReservationEvent.AddReservationEventHandler;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class AddReservationEvent extends  GwtEvent<AddReservationEventHandler> {

	public static interface AddReservationEventHandler extends EventHandler {
		void addRequested(AddReservationEvent event);
	}

	public static final Type<AddReservationEventHandler> TYPE = new Type<AddReservationEventHandler>();
	
	
	@Override
	public com.google.gwt.event.shared.GwtEvent.Type<AddReservationEventHandler> getAssociatedType() {
		// TODO Auto-generated method stub
		return TYPE;
	}

	@Override
	protected void dispatch(AddReservationEventHandler handler) {
		handler.addRequested(this);
		
	}


}
