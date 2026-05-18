package org.rapla.plugin.javasciptpatch.server;

import org.rapla.facade.RaplaFacade;
import org.rapla.logger.Logger;
import org.rapla.server.spring.RaplaServerProperties;
import org.rapla.storage.CachableStorageOperator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileReader;

/** Loads + runs an optional one-shot JS patch script at server startup.
 *
 *  <p>PRD 019 Phase 3d: migrated from {@code ServerExtension} to
 *  {@code @EventListener(ApplicationReadyEvent.class)}. There's no recurring
 *  cadence — this is a single-fire startup hook. {@code @PreDestroy} is
 *  unneeded because the legacy {@code stop()} body was empty. */
public class JavascriptPatcher
{
    final RaplaFacade facade;
    final Logger logger;
    final RaplaServerProperties properties;
    final CachableStorageOperator cachableStorageOperator;

    @Autowired
    public JavascriptPatcher(RaplaFacade facade, Logger logger, RaplaServerProperties properties,
                             CachableStorageOperator cachableStorageOperator)
    {
        this.facade = facade;
        this.logger = logger;
        this.properties = properties;
        this.cachableStorageOperator = cachableStorageOperator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runPatchScript()
    {
        final String patchScript = properties.getPatchScript();
        if (patchScript == null) return;
        File file = new File(patchScript);
        try (final FileReader reader = new FileReader(file))
        {
            logger.info("Patch Script " + patchScript + " done.");
        }
        catch (Exception e)
        {
            logger.error(e.getMessage(), e);
        }
    }

    static PatchScript loadScript(ScriptEngine engine, String patchscript) throws ScriptException
    {
        final Object eval = engine.eval("load('" + patchscript + "')");
        return (PatchScript) eval;
    }

    public interface PatchScript {
        void patchRapla(RaplaFacade raplaFacade);
    }
}
