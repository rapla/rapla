package org.rapla.client.spring;

import org.junit.jupiter.api.Test;
import org.rapla.client.api.ClientService;
import org.rapla.client.swing.internal.RaplaClientServiceImpl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginDialogExitTest
{
    @Test
    void exitAction_signalsLogoutSoProcessCanTerminate() throws Exception
    {
        try (SpringRaplaClient client = new SpringRaplaClient())
        {
            RaplaClientServiceImpl service = (RaplaClientServiceImpl) client.getContext().getBean(ClientService.class);
            LogoutSignal signal = client.getContext().getBean(LogoutSignal.class);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<NextSession> future = executor.submit(signal::take);

            service.notifyLoginAborted();

            NextSession result = future.get(2, TimeUnit.SECONDS);
            assertTrue(result.isExit(),
                    "Login-dialog Exit button must signal NextSession.exit() so SpringRaplaClient.main() terminates");
            executor.shutdown();
        }
    }
}
