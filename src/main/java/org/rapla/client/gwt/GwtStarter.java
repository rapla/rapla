package org.rapla.client.gwt;

import jsinterop.annotations.JsType;
import org.rapla.inject.client.gwt.GwtComponentMarker;
import org.rapla.scheduler.Promise;
import org.rapla.storage.dbrm.LoginTokens;

@JsType
public interface GwtStarter extends GwtComponentMarker
{
    LoginTokens getValidToken();

    Promise<Void> initLocale(String localeParam);

    Promise<JsApi> registerApi(String loginToken);


}
