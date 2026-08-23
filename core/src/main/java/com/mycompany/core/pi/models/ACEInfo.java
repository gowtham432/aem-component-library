package com.mycompany.core.pi.models;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single rep:GrantACE / rep:DenyACE node, resolved to the content path it
 * actually governs (the parent of the rep:policy node it lives under).
 */
public class ACEInfo {

    private final String principal;
    private final String type;
    private final List<String> privileges;
    private final List<String> literalPrivileges;
    private final Map<String, String> restrictions = new LinkedHashMap<>();
    private final String nodePath;
    private final String contentPath;

    public ACEInfo(String principal, String type, List<String> privileges, String nodePath, String contentPath) {
        this(principal, type, privileges, privileges, nodePath, contentPath);
    }

    /**
     * @param privileges        the full set used for matching -- includes every aggregate
     *                          privilege implied by what's literally on the ACE (see
     *                          ACLCollectionServiceImpl)
     * @param literalPrivileges exactly what the ACE itself declares (e.g. just
     *                          {@code jcr:all}), for display when explaining which rule
     *                          actually granted a checked privilege that isn't itself listed
     */
    public ACEInfo(String principal, String type, List<String> privileges, List<String> literalPrivileges,
            String nodePath, String contentPath) {
        this.principal = principal;
        this.type = type;
        this.privileges = privileges;
        this.literalPrivileges = literalPrivileges;
        this.nodePath = nodePath;
        this.contentPath = contentPath;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getType() {
        return type;
    }

    public List<String> getPrivileges() {
        return privileges;
    }

    public List<String> getLiteralPrivileges() {
        return literalPrivileges;
    }

    public Map<String, String> getRestrictions() {
        return restrictions;
    }

    public String getNodePath() {
        return nodePath;
    }

    public String getContentPath() {
        return contentPath;
    }
}
