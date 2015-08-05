package org.rapla.client.gwt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.rapla.client.ActivityManager;
import org.rapla.client.Application;
import org.rapla.client.event.DetailEndEvent;
import org.rapla.client.event.DetailSelectEvent;
import org.rapla.entities.Entity;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.storage.StorageOperator;

import com.google.gwt.user.client.History;
import com.google.web.bindery.event.shared.EventBus;

@Singleton
public class ActivityManagerImpl extends ActivityManager {

	private static final String EDIT_PREFIX = "edit=";
	private static final String DELIMITER = ",";
	private Place place;
	private final List<String> edits = new ArrayList<String>();
	@Inject
	private ClientFacade facade;

	@Inject
	public ActivityManagerImpl(Application application, EventBus eventBus) {
		super(application, eventBus);
	}

	@Override
	public void init() throws RaplaException {
		// theory, this class is loaded on startup, so check the url and fire
		// events
		final String token = History.getToken();
		if (token != null && !token.isEmpty()) {
			if (token.contains(EDIT_PREFIX)) {
				String parameters = token.substring(token.indexOf(EDIT_PREFIX) + EDIT_PREFIX.length());
				final int indexOfNextParamteter = parameters.indexOf("&");
				if (indexOfNextParamteter > 0) {
					parameters = parameters.substring(0, indexOfNextParamteter);
				}
				final List<String> parsedIds = Arrays.asList(parameters.split(DELIMITER));
				final StorageOperator operator = facade.getOperator();
				final Map<String, Entity> entities = operator.getFromId(
						parsedIds, false);
				final Collection<Entity> values = entities.values();
				for (Entity entity : values) {
					if (entity != null) {
						detailsRequested(new DetailSelectEvent(entity, null));
					}
				}
			}
		}
	}

	@Override
	protected void createActivityOrPlace(DetailSelectEvent event) {
		final Entity<?> selectedObject = event.getSelectedObject();
		if (selectedObject != null && !edits.contains(selectedObject.getId())) {
			edits.add(selectedObject.getId());
			createHistroryEntry();
		}
	}

	private void createHistroryEntry() {
		final StringBuilder sb = new StringBuilder();
		if (place != null) {
			sb.append(place.toString());
		}
		if (!edits.isEmpty()) {
			sb.append("?");
			sb.append(EDIT_PREFIX);
			boolean commaNeeded = false;
			for (String edit : edits) {
				if (commaNeeded) {
					sb.append(DELIMITER);
				} else {
					commaNeeded = true;
				}
				sb.append(edit);
			}
		}
		History.newItem(sb.toString());
	}

	@Override
	public void detailsEnded(DetailEndEvent event) {
		final String token = History.getToken();
		if (token != null && !token.isEmpty()) {
			String eventId;
			if (event != null && event.getEntity() != null) {
				eventId = event.getEntity().getId();
			} else {
				eventId = "unknown";
			}
			if (this.edits.remove(eventId)) {
				createHistroryEntry();
			}
		}
	}

	private static class Place {
		private final String name;
		private final String id;

		public Place(String name, String id) {
			this.name = name;
			this.id = id;
		}

		public String getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			return new StringBuilder("#").append(name).append("/").append(id)
					.toString();
		}
	}

}
