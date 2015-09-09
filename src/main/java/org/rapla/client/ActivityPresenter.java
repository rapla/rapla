package org.rapla.client;

import org.rapla.client.ActivityManager.Activity;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context={ InjectionContext.gwt})
public interface ActivityPresenter
{

    boolean startActivity(Activity activity);
    
}
