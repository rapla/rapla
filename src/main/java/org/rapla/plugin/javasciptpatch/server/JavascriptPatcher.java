package org.rapla.plugin.javasciptpatch.server;

import org.rapla.facade.RaplaFacade;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.server.extensionpoints.ServerExtension;
import org.rapla.server.internal.ServerContainerContext;

import javax.inject.Inject;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.FileNotFoundException;
import java.io.FileReader;

@Extension(provides = ServerExtension.class,id="org.rapla.plugin.javascriptpatch.server")
public class JavascriptPatcher  implements ServerExtension
{
    final RaplaFacade facade;

    @Inject
    Logger logger;

    @Inject
    ServerContainerContext serverContainerContext;

    @Inject
    public JavascriptPatcher(RaplaFacade facade)
    {
        this.facade = facade;
    }

    @Override
    public void start()
    {
        final String patchScript = serverContainerContext.getPatchScript();
        if ( patchScript != null)
        {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");

            try (final FileReader reader = new FileReader(patchScript))
            {
                logger.info( "Executing Patch Script " + patchScript);
                engine.eval(reader);
                ((Invocable)engine).invokeFunction("patchRapla", facade);
                logger.info( "Patch Script " + patchScript  + " done.");
            }
            catch (Exception e)
            {
                logger.error(e.getMessage(), e);
            }

        }

    }

    @Override
    public void stop()
    {

    }
}
