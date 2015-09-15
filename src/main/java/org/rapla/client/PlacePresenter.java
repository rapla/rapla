package org.rapla.client;

import org.rapla.client.ActivityManager.Place;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context={ InjectionContext.gwt},id="place")
public interface PlacePresenter
{
    /**
     * Provides the view information from this place. Could be HTML, Swing, Android or other code.
     * @return
     *      the view of this place
     */
    Object provideContent();

    /**
     * Forces the view to update its state. Can be triggered because changes where made by someone 
     * else or because of changes done by the user itself.
     */
    void updateView();
    
    void initForPlace(Place place);

    /**
     * Is called, when this presenter is the first presenter in the list of all places presenter and no place is provided so
     * the presenter can reset itself to its initial state.
     */
    void resetPlace();

}
