package org.rapla.plugin.ical;

import org.rapla.framework.RaplaException;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@Path("ical/import")
public interface ICalImport
{
    @POST
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
    Integer[] importICal(Import job) throws RaplaException;

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    class Import
    {
        private String content;
        private boolean isURL;
        private String[] allocatableIds;
        private String eventTypeKey;
        private String eventTypeNameAttributeKey;

        public Import()
        {
        }

        public Import(String content, boolean isURL, String[] allocatableIds, String eventTypeKey, String eventTypeNameAttributeKey)
        {
            super();
            this.content = content;
            this.isURL = isURL;
            this.allocatableIds = allocatableIds;
            this.eventTypeKey = eventTypeKey;
            this.eventTypeNameAttributeKey = eventTypeNameAttributeKey;
        }

        public String getContent()
        {
            return content;
        }

        public boolean isURL()
        {
            return isURL;
        }

        public String[] getAllocatableIds()
        {
            return allocatableIds;
        }

        public String getEventTypeKey()
        {
            return eventTypeKey;
        }

        public String getEventTypeNameAttributeKey()
        {
            return eventTypeNameAttributeKey;
        }

    }
}