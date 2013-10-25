package org.rapla.plugin.mail;

import org.rapla.framework.RaplaException;

public interface MailConfigService
{
	boolean isExternalConfigEnabled() throws RaplaException;
}
