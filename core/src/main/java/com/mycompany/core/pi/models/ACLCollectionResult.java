package com.mycompany.core.pi.models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every ACE found for a set of principals, grouped by the content path each
 * ACE governs. On failure, {@code error} is set and {@code aclsByPath} is empty.
 */
public class ACLCollectionResult {

    private String rootPath;
    private int totalACEs;
    private Map<String, List<ACEInfo>> aclsByPath = new LinkedHashMap<>();
    private String error;

    public static ACLCollectionResult error(String rootPath, String message) {
        ACLCollectionResult result = new ACLCollectionResult();
        result.rootPath = rootPath;
        result.error = message;
        return result;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public int getTotalACEs() {
        return totalACEs;
    }

    public void setTotalACEs(int totalACEs) {
        this.totalACEs = totalACEs;
    }

    public Map<String, List<ACEInfo>> getAclsByPath() {
        return aclsByPath;
    }

    public void addACE(ACEInfo ace) {
        aclsByPath.computeIfAbsent(ace.getContentPath(), p -> new ArrayList<>()).add(ace);
        totalACEs++;
    }

    public String getError() {
        return error;
    }

    public boolean isSuccess() {
        return error == null;
    }
}
