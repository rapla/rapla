package org.rapla.client.gwt;

import org.rapla.client.UserClientService;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;

@DefaultImplementation(of=UserClientService.class, context = InjectionContext.gwt)
public class UserClientServiceGwt implements UserClientService
{
    @Inject
    public UserClientServiceGwt()
    {

    }
    @Override
    public boolean isRunning()
    {
        return true;
    }

    @Override
    public void switchTo(User user) throws RaplaException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canSwitchBack()
    {
        return false;
    }

    @Override
    public void restart()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLogoutAvailable()
    {
        return false;
    }

    @Override
    public void logout()
    {
        throw new UnsupportedOperationException();
    }
}
