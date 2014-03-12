package org.rapla.plugin.mail;

import javax.jws.WebService;

import org.rapla.framework.RaplaException;

@WebService
public interface MailConfigService
{
	boolean isExternalConfigEnabled() throws RaplaException;
}
