package org.rapla.components.i18n;

public class I18nIconURL implements I18nIcon
{
    private final String key;
    private final String url;

    public I18nIconURL(String key, String url) {
        this.key = key;
        this.url = url;
    }

    @Override
    public String getId() {
        return key;
    }

    public String getUrl() {
        return url;
    }
}
