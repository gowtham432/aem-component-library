package com.mycompany.core.pi.models;

import java.util.List;

/**
 * A pointer back to the specific ACE that decided (or was overridden in)
 * a permission resolution, plus how the user reaches that ACE's principal.
 */
public class RuleReference {

    private final String principal;
    private final String type;
    private final String setAtPath;
    private final String glob;
    private final List<String> via;
    private final List<String> privileges;

    public RuleReference(String principal, String type, String setAtPath, String glob, List<String> via) {
        this(principal, type, setAtPath, glob, via, null);
    }

    /**
     * @param privileges what the ACE itself literally grants/denies (e.g. {@code jcr:all}) --
     *                   may differ from whichever privilege a caller is checking against this
     *                   rule, since a broad grant like jcr:all can decide a narrower check
     */
    public RuleReference(String principal, String type, String setAtPath, String glob, List<String> via,
            List<String> privileges) {
        this.principal = principal;
        this.type = type;
        this.setAtPath = setAtPath;
        this.glob = glob;
        this.via = via;
        this.privileges = privileges;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getType() {
        return type;
    }

    public String getSetAtPath() {
        return setAtPath;
    }

    public String getGlob() {
        return glob;
    }

    public List<String> getVia() {
        return via;
    }

    public List<String> getPrivileges() {
        return privileges;
    }
}
