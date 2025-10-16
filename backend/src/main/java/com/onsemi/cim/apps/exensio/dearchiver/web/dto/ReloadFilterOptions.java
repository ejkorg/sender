package com.onsemi.cim.apps.exensio.dearchiver.web.dto;

import java.util.ArrayList;
import java.util.List;

public class ReloadFilterOptions {
    private List<String> locations = new ArrayList<>();
    private List<String> dataTypes = new ArrayList<>();
    private List<String> testerTypes = new ArrayList<>();
    private List<String> dataTypeExt = new ArrayList<>();
    private List<String> fileTypes = new ArrayList<>();

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public List<String> getDataTypes() {
        return dataTypes;
    }

    public void setDataTypes(List<String> dataTypes) {
        this.dataTypes = dataTypes;
    }

    public List<String> getTesterTypes() {
        return testerTypes;
    }

    public void setTesterTypes(List<String> testerTypes) {
        this.testerTypes = testerTypes;
    }

    public List<String> getDataTypeExt() {
        return dataTypeExt;
    }

    public void setDataTypeExt(List<String> dataTypeExt) {
        this.dataTypeExt = dataTypeExt;
    }

    public List<String> getFileTypes() {
        return fileTypes;
    }

    public void setFileTypes(List<String> fileTypes) {
        this.fileTypes = fileTypes;
    }
}
