/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbfile;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import javax.inject.Named;

import org.rapla.RaplaResources;
import org.rapla.components.util.CommandScheduler;
import org.rapla.components.util.xml.RaplaContentHandler;
import org.rapla.components.util.xml.RaplaErrorHandler;
import org.rapla.components.util.xml.RaplaSAXHandler;
import org.rapla.components.util.xml.XMLReaderAdapter;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.domain.Permission;
import org.rapla.entities.domain.PermissionContainer;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.internal.ModifiableTimestamp;
import org.rapla.entities.storage.ImportExportEntity;
import org.rapla.entities.storage.RefEntity;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.logger.Logger;
import org.rapla.server.ServerService;
import org.rapla.storage.LocalCache;
import org.rapla.storage.PreferencePatch;
import org.rapla.storage.UpdateEvent;
import org.rapla.storage.UpdateResult;
import org.rapla.storage.impl.AbstractCachableOperator;
import org.rapla.storage.impl.EntityStore;
import org.rapla.storage.impl.server.EntityHistory;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;
import org.rapla.storage.xml.IOContext;
import org.rapla.storage.xml.RaplaDefaultXMLContext;
import org.rapla.storage.xml.RaplaMainReader;
import org.rapla.storage.xml.RaplaMainWriter;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/** Use this Operator to keep the data stored in an XML-File.
 @see AbstractCachableOperator
 @see org.rapla.storage.StorageOperator
 */
final public class FileOperator extends LocalAbstractCachableOperator
{
    protected URI storageURL;

    final boolean includeIds = false;

    static FileIO DefaultFileIO = new DefaultFileIO();
    FileIO FileIO = DefaultFileIO;

    public void setFileIO(FileIO fileIO)
    {
        FileIO = fileIO;
    }

    static public void setDefaultFileIO(FileIO fileIO)
    {
        DefaultFileIO = fileIO;
    }
    public interface FileIO
    {
        InputSource getInputSource(URI storageURL) throws IOException;
        void write(RaplaWriter writer, URI storageURL) throws IOException;
    }
    static  public class DefaultFileIO implements FileIO
    {
        public InputSource getInputSource(URI storageURL) throws IOException
        {
            return new InputSource(storageURL.toURL().toExternalForm().toString());
        }

        public void write(RaplaWriter writer, URI storageURL) throws IOException
        {
            final String encoding = "utf-8";
            File storageFile = new File( storageURL);
            final String newPath = storageFile.getPath() + ".new";
            final String backupPath = storageFile.getPath() + ".bak";
            final File newFile = new File(newPath);
            try (OutputStream outNew = new FileOutputStream(newFile))
            {
                BufferedWriter w = new BufferedWriter(new OutputStreamWriter(outNew, encoding));
                writer.write(w);
                w.flush();
                w.close();
                File parentFile = storageFile.getParentFile();
                if (!parentFile.exists())
                {
                    parentFile.mkdirs();
                }
                moveFile(storageFile, backupPath);
                moveFile(newFile, storageFile.getPath());
            }
        }

        private void moveFile(File file, String newPath)
        {
            File backupFile = new File(newPath);
            backupFile.delete();
            file.renameTo(backupFile);
        }

    }



    public FileOperator(Logger logger, RaplaResources i18n, RaplaLocale raplaLocale, CommandScheduler scheduler,
            Map<String, FunctionFactory> functionFactoryMap, @Named(ServerService.ENV_RAPLAFILE_ID) String resolvedPath,
            Set<PermissionExtension> permissionExtensions) throws RaplaException
    {
        super(logger, i18n, raplaLocale, scheduler, functionFactoryMap, permissionExtensions);
        try
        {
            storageURL = new File(resolvedPath).getCanonicalFile().toURI();
        }
        catch (Exception e)
        {
            throw new RaplaException("Error parsing file '" + resolvedPath + "' " + e.getMessage());
        }
        //        boolean validate = config.getChild( "validate" ).getValueAsBoolean( false );
        //        if ( validate )
        //        {
        //            getLogger().error("Validation currently not supported");
        //        }
        //        includeIds = config.getChild( "includeIds" ).getValueAsBoolean( false );

    }

    public String getURL()
    {
        return storageURL.toString();
    }

    public boolean supportsActiveMonitoring()
    {
        return false;
    }

    @Override public Date getHistoryValidStart()
    {
        Date connectStart = getConnectStart();
        final Date date = new Date(Math.max(getLastRefreshed().getTime() - HISTORY_DURATION, connectStart.getTime()));
        return date;
    }

    /** Sets the isConnected-flag and calls loadData.*/
    @Override
    final public void connect() throws RaplaException
    {
        if (!isConnected())
        {
            getLogger().info("Connecting: " + getURL());
            cache.clearAll();
            addInternalTypes(cache);
            loadData(cache);
            changeStatus(InitStatus.Loaded);
            initIndizes();
            changeStatus(InitStatus.Connected);

        }
        /*
        if ( connectInfo != null)
        {
            final String username = connectInfo.getUsername();
            final UserImpl user = cache.getUser(username);
            if (user == null)
            {
                throw new RaplaSecurityException("User " + username + " not found!");
            }
            return user;
        }
        else
        {
            return null;
        }*/
    }


    final public void disconnect() throws RaplaException
    {
        super.disconnect();
    }

    @Override
    protected void refreshWithoutLock()
    {
        //getLogger().warn("Incremental refreshs are not supported");
        setLastRefreshed( getCurrentTimestamp());
        // TODO check if file timestamp has changed and either abort server with warning or refresh all data
    }



    final protected void loadData(LocalCache cache) throws RaplaException
    {
        if (getLogger().isDebugEnabled())
            getLogger().debug("Reading data from file:" + getURL());

        // TODO implement history storage
        // FIXME implement importexport storage
        Date lastUpdated = getCurrentTimestamp();
        setLastRefreshed(lastUpdated);
        setConnectStart(lastUpdated);

        EntityStore entityStore = new EntityStore(cache);
        RaplaDefaultXMLContext inputContext = new IOContext().createInputContext(logger, raplaLocale, i18n, entityStore, this);
        RaplaMainReader contentHandler = new RaplaMainReader(inputContext);
        try
        {
            parseData(contentHandler);
        }
        catch (FileNotFoundException ex)
        {
            getLogger().warn("Data file not found " + getURL() + " creating default system.");
            createDefaultSystem(cache);
            return;
        }
        catch (IOException ex)
        {
            getLogger().warn("Loading error: " + getURL());
            throw new RaplaException("Can't load file at " + getURL() + ": " + ex.getMessage());
        }
        try
        {
            Collection<Entity> list = entityStore.getList();
            cache.putAll(list);
            Preferences preferences = cache.getPreferencesForUserId(null);
            if (preferences != null)
            {
                TypedComponentRole<RaplaConfiguration> oldEntry = new TypedComponentRole<RaplaConfiguration>("org.rapla.plugin.export2ical");
                if (preferences.getEntry(oldEntry, null) != null)
                {
                    preferences.putEntry(oldEntry, null);
                }
                RaplaConfiguration entry = preferences.getEntry(RaplaComponent.PLUGIN_CONFIG, null);
                if (entry != null)
                {
                    DefaultConfiguration pluginConfig = (DefaultConfiguration) entry.find("class", "org.rapla.export2ical.Export2iCalPlugin");
                    entry.removeChild(pluginConfig);
                }
            }

            resolveInitial(list, this);
            // It is important to do the read only later because some resolve might involve write to referenced objects
            if (inputContext.lookup(RaplaMainReader.VERSION) < 1.2)
            {
                migrateSpecialAttributes(list);
            }
            Collection<Entity> migratedTemplates = migrateTemplates();
            cache.putAll(migratedTemplates);
            removeInconsistentEntities(cache, list);
            for (Entity entity : migratedTemplates)
            {
                ((RefEntity) entity).setReadOnly();
            }
            for (Entity entity : list)
            {
                ((RefEntity) entity).setReadOnly();
            }
            cache.getSuperCategory().setReadOnly();
            for (User user : cache.getUsers())
            {
                ReferenceInfo<User> id = user.getReference();
                String password = entityStore.getPassword(id);
                //System.out.println("Storing password in cache" + password);
                cache.putPassword(id, password);
            }
            // contextualize all Entities
            if (getLogger().isDebugEnabled())
                getLogger().debug("Entities contextualized");
        }
        catch (RaplaException ex)
        {
            throw ex;
        }
    }

    private void migrateSpecialAttributes(Collection<Entity> list)
    {
        for (Entity entity : list)
        {
            if (entity instanceof Classifiable && entity instanceof PermissionContainer)
            {
                Classification c = ((Classifiable) entity).getClassification();
                PermissionContainer permCont = (PermissionContainer) entity;
                if (c == null)
                {
                    continue;
                }
                Attribute attribute = c.getAttribute("permission_modify");
                if (attribute == null)
                {
                    continue;
                }
                Collection<Object> values = c.getValues(attribute);
                if (values == null || values.size() == 0)
                {
                    continue;
                }
                if (attribute.getType() == AttributeType.BOOLEAN)
                {
                    if (values.iterator().next().equals(Boolean.TRUE))
                    {
                        Permission permission = permCont.newPermission();
                        permission.setAccessLevel(Permission.ADMIN);
                        permCont.addPermission(permission);
                    }
                }
                else if (attribute.getType() == AttributeType.CATEGORY)
                {
                    for (Object value : values)
                    {
                        Permission permission = permCont.newPermission();
                        permission.setAccessLevel(Permission.ADMIN);
                        permission.setGroup((Category) value);
                        permCont.addPermission(permission);
                    }
                }
            }
        }
    }

    private void parseData(RaplaSAXHandler reader) throws RaplaException, IOException
    {
        ContentHandler contentHandler = new RaplaContentHandler(reader);
        try
        {
            InputSource source = FileIO.getInputSource(storageURL);
            XMLReader parser = XMLReaderAdapter.createXMLReader(false);
            RaplaErrorHandler errorHandler = new RaplaErrorHandler(getLogger().getChildLogger("reading"));
            parser.setContentHandler(contentHandler);
            parser.setErrorHandler(errorHandler);
            parser.parse(source);
        }
        catch (SAXException ex)
        {
            Throwable cause = ex.getCause();
            while (cause != null && cause.getCause() != null)
            {
                cause = cause.getCause();
            }
            if (ex instanceof SAXParseException)
            {
                throw new RaplaException(
                        "Line: " + ((SAXParseException) ex).getLineNumber() + " Column: " + ((SAXParseException) ex).getColumnNumber() + " " + ((cause
                                != null) ? cause.getMessage() : ex.getMessage()), (cause != null) ? cause : ex);
            }
            if (cause == null)
            {
                throw new RaplaException(ex);
            }
            if (cause instanceof RaplaException)
                throw (RaplaException) cause;
            else
                throw new RaplaException(cause);
        }
            /*  End of Exception Handling */
    }

    public void dispatch(final UpdateEvent evt) throws RaplaException
    {

        final Lock writeLock = writeLock();
        try
        {
            Date since = evt.getLastValidated();
            preprocessEventStorage(evt);
            updateHistory(evt);
            Date until = getCurrentTimestamp();
            // call of update must be first to update the cache.
            // then saveData() saves all the data in the cache
            final Collection<ReferenceInfo> removeIds = evt.getRemoveIds();
            final List<PreferencePatch> preferencePatches = evt.getPreferencePatches();
            final Collection<Entity> storeObjects = evt.getStoreObjects();
            UpdateResult result = refresh(since, until, storeObjects, preferencePatches, removeIds);
            saveData(cache, null, includeIds);
        }
        finally
        {
            unlock(writeLock);
        }
        // TODO check if still needed
        //fireStorageUpdated(result);
    }

    private void updateHistory(UpdateEvent evt) throws RaplaException {
        String userId = evt.getUserId();
        User lastChangedBy =  ( userId != null) ?  resolve(userId,User.class) : null;
        try {
            Thread.sleep(1);
        } catch (InterruptedException e1) {
            throw new RaplaException( e1.getMessage(), e1);
        }
        Date currentTime = getCurrentTimestamp();
        for ( Entity e: evt.getStoreObjects())
        {
            final boolean isDelete = false;
            if ( e instanceof ModifiableTimestamp)
            {
                ModifiableTimestamp modifiableTimestamp = (ModifiableTimestamp)e;
                modifiableTimestamp.setLastChanged( currentTime);
                modifiableTimestamp.setLastChangedBy( lastChangedBy );
                history.addHistoryEntry(e,currentTime, isDelete);
            }
        }
        for ( ReferenceInfo id: evt.getRemoveIds())
        {
            if ( EntityHistory.isSupportedEntity( id.getType()))
            {
                final Entity e = tryResolve(id);
                final boolean isDelete = true;
                history.addHistoryEntry(e, currentTime, isDelete);
            }
        }
        for ( PreferencePatch patch: evt.getPreferencePatches())
        {
            patch.setLastChanged( currentTime );
        }
    }


    synchronized final public void saveData() throws RaplaException
    {
        final Lock writeLock = writeLock();
        try
        {
            saveData(cache, null, includeIds);
        }
        finally
        {
            unlock(writeLock);
        }
    }

    synchronized final public void saveData(LocalCache cache, String version) throws RaplaException
    {
        saveData(cache, version, true);
    }

    synchronized final private void saveData(LocalCache cache, String version, boolean includeIds) throws RaplaException
    {
        final RaplaMainWriter raplaMainWriter = getMainWriter(cache, version, includeIds);
        try
        {
            FileIO.write(new RaplaWriter()
            {
                @Override public void write(BufferedWriter writer) throws IOException
                {
                    raplaMainWriter.setWriter(writer);
                    raplaMainWriter.printContent();
                }
            }, storageURL);
        }
        catch (IOException e)
        {
            throw new RaplaException(e.getMessage());
        }
    }

    /**
     * Override for custom read
     */
    public interface RaplaWriter
    {
        void write(BufferedWriter writer) throws IOException;
    }

    private RaplaMainWriter getMainWriter(LocalCache cache, String version, boolean includeIds)
    {
        RaplaDefaultXMLContext outputContext = new IOContext().createOutputContext(logger, raplaLocale, i18n, cache.getSuperCategoryProvider(), includeIds);
        RaplaMainWriter writer = new RaplaMainWriter(outputContext, cache);
        writer.setEncoding("utf-8");
        if (version != null)
        {
            writer.setVersion(version);
        }
        return writer;
    }


    public String toString()
    {
        return "FileOperator for " + getURL();
    }
    
    private static class SystemLock
    {
        private Date lastRequested;
        private boolean active;
    }
    private final Map<String, SystemLock> locks = new HashMap<String, SystemLock>();

    @Override
    public Date getLock(String id, Long validMilliseconds) throws RaplaException
    {
        SystemLock systemLock = locks.get(id);
        if(systemLock == null)
        {
            systemLock = new SystemLock();
            locks.put(id, systemLock);
        }
        if(systemLock.active){
            throw new RaplaException("Lock already in use");
        }
        systemLock.lastRequested = new Date();
        systemLock.active = true;
        return systemLock.lastRequested;
    }
    
    @Override
    public void releaseLock(String id, Date updatedUntil)
    {
        final SystemLock systemLock = locks.get(id);
        if(systemLock != null)
        {
            if(updatedUntil != null)
            {
                systemLock.lastRequested = updatedUntil;
            }
            systemLock.active = false;
        }
    }
    
    @Override
    public Collection<ImportExportEntity> getImportExportEntities(String systemId, int importExportDirection)
    {
        // FIXME implement me
        return Collections.emptyList();
    }

}
