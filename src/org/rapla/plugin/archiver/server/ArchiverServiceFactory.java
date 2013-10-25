package org.rapla.plugin.archiver.server;

import org.rapla.entities.User;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.archiver.ArchiverService;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

public class ArchiverServiceFactory extends RaplaComponent implements RemoteMethodFactory<ArchiverService>
{

	public ArchiverServiceFactory(RaplaContext context) {
		super(context);
	}

	public ArchiverService createService(final RemoteSession remoteSession) {
		RaplaContext context = getContext();
		return new ArchiverServiceImpl( context)
		{
			@Override
			protected void checkAccess() throws RaplaException {
				User user = remoteSession.getUser();
				if ( user != null && !user.isAdmin())
				{
					throw new RaplaSecurityException("ArchiverService can only be triggered by admin users");
				}
			}
		};
	}

}
