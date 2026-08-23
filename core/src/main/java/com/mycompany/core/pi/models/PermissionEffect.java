package com.mycompany.core.pi.models;

import java.util.ArrayList;
import java.util.List;

/**
 * The resolved effect for one (path, privilege) pair, with a trace back to
 * the ACE that decided it and every ACE it beat out along the way.
 */
public class PermissionEffect {

    private final String effect;
    private final RuleReference decidingRule;
    private final List<RuleReference> overriddenRules = new ArrayList<>();
    private final boolean inherited;
    private String anomaly;
    private String verifiedEffect;
    private boolean resolverMismatch;

    public PermissionEffect(String effect, RuleReference decidingRule, boolean inherited) {
        this.effect = effect;
        this.decidingRule = decidingRule;
        this.inherited = inherited;
    }

    public String getEffect() {
        return effect;
    }

    public RuleReference getDecidingRule() {
        return decidingRule;
    }

    public List<RuleReference> getOverriddenRules() {
        return overriddenRules;
    }

    public boolean isInherited() {
        return inherited;
    }

    public String getAnomaly() {
        return anomaly;
    }

    public void setAnomaly(String anomaly) {
        this.anomaly = anomaly;
    }

    /**
     * The real effect per {@link javax.jcr.security.AccessControlManager#hasPrivileges},
     * or null if verification wasn't attempted or failed for this entry.
     */
    public String getVerifiedEffect() {
        return verifiedEffect;
    }

    public void setVerifiedEffect(String verifiedEffect) {
        this.verifiedEffect = verifiedEffect;
    }

    /**
     * True if {@link #getVerifiedEffect()} disagrees with {@link #getEffect()} --
     * the resolver's trace is a best-effort explanation, not ground truth.
     */
    public boolean isResolverMismatch() {
        return resolverMismatch;
    }

    public void setResolverMismatch(boolean resolverMismatch) {
        this.resolverMismatch = resolverMismatch;
    }
}
