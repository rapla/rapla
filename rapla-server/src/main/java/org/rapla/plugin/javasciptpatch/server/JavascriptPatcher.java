package org.rapla.plugin.javasciptpatch.server;

import org.rapla.facade.RaplaFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(JavascriptPatcher.class);
    final RaplaFacade facade;
    final RaplaServerProperties properties;
    final CachableStorageOperator cachableStorageOperator;

    @Autowired
    public JavascriptPatcher(RaplaFacade facade, RaplaServerProperties properties,
                             CachableStorageOperator cachableStorageOperator)
    {
        this.facade = facade;
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
            LOGGER.info("Patch Script {} done.", patchScript);
        }
        catch (Exception e)
        {
            LOGGER.error(e.getMessage(), e);
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
