package org.rapla.plugin.javasciptpatch.server;

import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.entities.storage.ExternalSyncEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaFacade;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.server.extensionpoints.ServerExtension;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.storage.CachableStorageOperator;

import javax.inject.Inject;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Extension(provides = ServerExtension.class,id="org.rapla.plugin.javascriptpatch.server")
public class JavascriptPatcher  implements ServerExtension
{
    final RaplaFacade facade;

    @Inject
    Logger logger;

    @Inject
    ServerContainerContext serverContainerContext;

    @Inject
    CachableStorageOperator cachableStorageOperator;

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
            //ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            File file = new File(patchScript);
            //final String absolutePath = file.getAbsolutePath();
            try (final FileReader reader = new FileReader(file))
            {
                logger.info( "Patch Script " + patchScript  + " done.");
            }
            catch (Exception e)
            {
                logger.error(e.getMessage(), e);
            }

        }

    }
    static PatchScript loadScript(ScriptEngine engine,String patchscript) throws ScriptException
    {
        final Object eval = engine.eval("load('" + patchscript + "')");
        return (PatchScript) eval;
    }

    public interface PatchScript {
        void patchRapla(RaplaFacade raplaFacade);
    }

    @Override
    public void stop()
    {

    }
}
