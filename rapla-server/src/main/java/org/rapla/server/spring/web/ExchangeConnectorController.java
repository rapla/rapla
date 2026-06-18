package org.rapla.server.spring.web;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.exchangeconnector.ExchangeConnectorRemote;
import org.rapla.plugin.exchangeconnector.SynchronizationStatus;
import org.rapla.plugin.exchangeconnector.server.SynchronisationManager;
import org.rapla.server.RemoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

/**
 * PRD 070 — Spring port of the legacy {@code ExchangeConnectorRemoteObjectFactory}
 * (dropped during the Spring Boot migration, which left {@code /api/exchange/connect}
 * unmapped → the Swing "Exchange Connector" dialog 404'd). Implements
 * {@link ExchangeConnectorRemote} (the interface owns routing per PRD 049) and delegates
 * each GUI/connect call to {@link SynchronisationManager} with the user resolved from the
 * session. All endpoints are per-user (own status/credentials/mailboxes only) — no
 * cross-user data, so no leak surface (AGENTS.md §12).
 */
@RestController
@ConditionalOnBean(RemoteSession.class)
public class ExchangeConnectorController implements ExchangeConnectorRemote
{
    private final SynchronisationManager manager;
    private final RemoteSession session;
    private final HttpServletRequest request;

    public ExchangeConnectorController(SynchronisationManager manager, RemoteSession session, HttpServletRequest request)
    {
        this.manager = manager;
        this.session = session;
        this.request = request;
    }

    @Override
    public SynchronizationStatus getSynchronizationStatus() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        return manager.getSynchronizationStatus(user);
    }

    @Override
    public void synchronize(String mailbox) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        manager.synchronizeUser(user, mailbox);
    }

    @Override
    public Collection<String> changeUser(String exchangeUsername, String exchangePassword) throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        return manager.changeUser(exchangeUsername, exchangePassword, user);
    }

    @Override
    public void removeUser() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        manager.removeTasksAndExports(user);
    }

    @Override
    public Collection<String> refreshMailboxes() throws RaplaException
    {
        User user = session.checkAndGetUser(request);
        return manager.refreshMailboxes(user);
    }
}
