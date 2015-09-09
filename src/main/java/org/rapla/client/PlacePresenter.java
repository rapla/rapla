package org.rapla.client;

import org.rapla.client.ActivityManager.Place;

public interface PlacePresenter
{
    Object provideContent();

    void updateView();

    boolean isResposibleFor(Place place);

    void resetPlace();

}
