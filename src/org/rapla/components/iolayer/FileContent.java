package org.rapla.components.iolayer;

import java.io.InputStream;

public class FileContent {
    String name;
    InputStream inputStream;
    public InputStream getInputStream() {
        return inputStream;
    }
    
    void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }
    
    public String getName() {
        return name;
    }
    
    void setName(String name) {
        this.name = name;
    }
}
