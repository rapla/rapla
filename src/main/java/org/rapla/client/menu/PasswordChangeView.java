package org.rapla.client.menu;

import org.rapla.client.RaplaWidget;

public interface PasswordChangeView extends RaplaWidget
{
    void dontShowOldPassword();

    char[] getOldPassword();

    char[] getNewPassword();

    char[] getPasswordVerification();
}
