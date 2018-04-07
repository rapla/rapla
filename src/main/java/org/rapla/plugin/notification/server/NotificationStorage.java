package org.rapla.plugin.notification.server;

import org.rapla.components.util.DateTools;
import org.rapla.entities.Entity;
import org.rapla.entities.storage.ImportExportDirections;
import org.rapla.entities.storage.ImportExportEntity;
import org.rapla.entities.storage.internal.ImportExportEntityImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.notification.server.NotificationService.AllocationMail;
import org.rapla.rest.JsonParserWrapper;
import org.rapla.storage.CachableStorageOperator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
public class NotificationStorage
{
    private final CachableStorageOperator operator;
    private final RaplaFacade facade;
    private final JsonParserWrapper.JsonParser gson = JsonParserWrapper.defaultJson().get();
    private  Map<String, ImportExportEntity> exportMails = new LinkedHashMap<>();
    private final Map<AllocationMail, String> mailToRaplaId = new LinkedHashMap<>();

    public static class NotificationContext
    {
        private int retryCount = 0;
        private long insertTimestamp;
    }

    @Inject
    public NotificationStorage(RaplaFacade facade)
    {
        this.facade = facade;
        this.operator = (CachableStorageOperator) facade.getOperator();
    }

    public synchronized Collection<AllocationMail> getMailsToSend() throws RaplaException
    {
        exportMails.clear();
        mailToRaplaId.clear();
        final ArrayList<AllocationMail> result = new ArrayList<>();
        exportMails = operator.getImportExportEntities(NotificationService.NOTIFICATION_LOCK_ID,
                ImportExportDirections.EXPORT);
        final long currentTimeMillis = System.currentTimeMillis();
        for (ImportExportEntity exportMailDb : exportMails.values())
        {
            final AllocationMail mail = gson.fromJson(exportMailDb.getData(), AllocationMail.class);
            final String id = exportMailDb.getId();
            mailToRaplaId.put(mail, id);
            final NotificationContext context = gson.fromJson(exportMailDb.getContext(), NotificationContext.class);
            final long nextTime = context.insertTimestamp + (context.retryCount + 1) * DateTools.MILLISECONDS_PER_MINUTE * 10;
            if (nextTime < currentTimeMillis)
            {
                result.add(mail);
            }
        }
        return result;
    }

    public void store(List<AllocationMail> mailList) throws RaplaException
    {
        final ArrayList<Entity> toStore = new ArrayList<>();
        for (AllocationMail allocationMail : mailList)
        {
            final ImportExportEntityImpl importExportEntityImpl = new ImportExportEntityImpl();
            final char[] charArray = UUID.randomUUID().toString().toCharArray();
            charArray[0] = 'n';
            importExportEntityImpl.setDirection(ImportExportDirections.EXPORT);
            importExportEntityImpl.setExternalSystem(NotificationService.NOTIFICATION_LOCK_ID);
            final String raplaId = new String(charArray);
            importExportEntityImpl.setId(raplaId);
            importExportEntityImpl.setData(gson.toJson(allocationMail));
            final NotificationContext context = new NotificationContext();
            // TODO think about getting timestamp from somewhere else
            context.insertTimestamp = System.currentTimeMillis();
            importExportEntityImpl.setContext(gson.toJson(context));
            toStore.add(importExportEntityImpl);
            exportMails.put(raplaId, importExportEntityImpl);
            mailToRaplaId.put(allocationMail, raplaId);
        }
        facade.storeObjects(toStore.toArray(Entity.ENTITY_ARRAY));
    }

    public void increateAndStoreRetryCount(AllocationMail mail) throws RaplaException
    {
        for (AllocationMail knownMail : mailToRaplaId.keySet())
        {
            if (knownMail.subject.equals(mail.subject) && knownMail.recipient.equals(mail.recipient) && knownMail.body.equals(mail.body))
            {
                final String exportId = mailToRaplaId.get(knownMail);
                final ImportExportEntity importExportEntity = exportMails.get(exportId);
                if (importExportEntity != null)
                {
                    final NotificationContext context = gson.fromJson(importExportEntity.getContext(), NotificationContext.class);
                    context.retryCount++;
                    final ImportExportEntityImpl edit = (ImportExportEntityImpl) facade.edit(importExportEntity);
                    edit.setContext(gson.toJson(context));
                    facade.store(importExportEntity);
                }
            }
        }
    }

    public synchronized void markSent(AllocationMail mail) throws RaplaException
    {
        AllocationMail allocMailToRemove = null;
        for (AllocationMail knownMail : mailToRaplaId.keySet())
        {
            if (knownMail.subject.equals(mail.subject) && knownMail.recipient.equals(mail.recipient) && knownMail.body.equals(mail.body))
            {
                final String exportId = mailToRaplaId.get(knownMail);
                final ImportExportEntity importExportEntity = exportMails.get(exportId);
                if (importExportEntity != null)
                {
                    facade.remove(importExportEntity);
                    allocMailToRemove = knownMail;
                    exportMails.remove(exportId);
                }
            }
        }
        if (allocMailToRemove != null)
        {
            mailToRaplaId.remove(allocMailToRemove);
        }
    }

}
