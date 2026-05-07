package org.rapla.plugin.ical;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/ical/import")
public interface ICalImport
{
    @PostExchange
    Integer[] importICal(@RequestBody Import job) throws RaplaException;

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