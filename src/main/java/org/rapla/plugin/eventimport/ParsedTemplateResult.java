package org.rapla.plugin.eventimport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParsedTemplateResult {

    List<String> header = new ArrayList<>();
    List<Map<String, String>> templateList = new ArrayList<>();
    
    public List<String> getHeader() {
        return header;
    }

    public List<Map<String, String>> getTemplateList() {
        return templateList;
    }

    public void setHeader(List<String> asList) {
        this.header = asList;
    }
    
    public void addTemplate(Map<String,String> row)
    {
        templateList.add( row );
    }

}
