package org.rapla.client;

import org.rapla.client.ActivityManager.Place;

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

    /**
     * Checks weather the presenter is responsible for this place or not.</br>
     * When the presenter is responsible for this place it can initialize itself using the informations from the place.
     * 
     * @param place the actual place of the application
     * @return when it is responsible for this place, the presenter must return <code>true</code> otherwise <code>false</code>.
     */
    boolean isResposibleFor(Place place);

    /**
     * Is called, when this presenter is the first presenter in the list of all places presenter and no place is provided so
     * the presenter can reset itself to its initial state.
     */
    void resetPlace();

}
