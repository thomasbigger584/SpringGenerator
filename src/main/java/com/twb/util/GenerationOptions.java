package com.twb.util;

public class GenerationOptions {

    private String entityName;

    private String extensionPrefix;

    private String packageName;

    private boolean supportsElasticSearch;

    private String appMainClass;

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getExtensionPrefix() {
        return extensionPrefix;
    }

    public void setExtensionPrefix(String extensionPrefix) {
        this.extensionPrefix = extensionPrefix;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean isSupportsElasticSearch() {
        return supportsElasticSearch;
    }

    public void setSupportsElasticSearch(boolean supportsElasticSearch) {
        this.supportsElasticSearch = supportsElasticSearch;
    }

    public String getAppMainClass() {
        return appMainClass;
    }

    public void setAppMainClass(String appMainClass) {
        this.appMainClass = appMainClass;
    }
}
