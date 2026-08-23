package com.mycompany.core.pi.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Root-cause diagnosis for one user's access to one path: a verdict per
 * checked privilege (Read / Edit / Full Access), each with the specific
 * rule responsible and what to do about it.
 */
public class AccessDiagnosisResult {

    private String userId;
    private String path;
    private final List<PrivilegeDiagnosis> checks = new ArrayList<>();
    private String error;

    public static AccessDiagnosisResult error(String userId, String path, String message) {
        AccessDiagnosisResult result = new AccessDiagnosisResult();
        result.userId = userId;
        result.path = path;
        result.error = message;
        return result;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<PrivilegeDiagnosis> getChecks() {
        return checks;
    }

    public String getError() {
        return error;
    }

    public boolean isSuccess() {
        return error == null;
    }
}
