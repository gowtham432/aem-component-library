package com.mycompany.core.pi.models;

import java.util.ArrayList;
import java.util.List;

/**
 * The verdict for one privilege at one path: whether the user is blocked,
 * the rule that decided it (per the resolver's trace, ground-truthed against
 * the real evaluator), every other group's rule that was in play but lost,
 * and a plain-English explanation of what to do about it.
 */
public class PrivilegeDiagnosis {

    private final String privilege;
    private final String label;
    private String resolverEffect;
    private String verifiedEffect;
    private boolean blocked;
    private boolean mismatch;
    private boolean inherited;
    private RuleReference decidingRule;
    // Kept internal-only (not serialized to the API response / UI, which now
    // shows only the deciding rule): still useful for test assertions that
    // verify the resolver's override detection itself is correct.
    private final transient List<RuleReference> overriddenRules = new ArrayList<>();
    private String remediation;
    private String aiExplanation;
    // Oak evaluates jcr:read independently of every other privilege, but
    // AEM/Sling resource resolution requires jcr:read to even reach a node --
    // so an ALLOW here can be technically correct yet practically unusable
    // when jcr:read is denied at the same path. Set only in that case; the
    // underlying Oak verdict (blocked/decidingRule above) is left untouched.
    private boolean effectivelyBlocked;
    private String effectiveBlockReason;

    public PrivilegeDiagnosis(String privilege, String label) {
        this.privilege = privilege;
        this.label = label;
    }

    public String getPrivilege() {
        return privilege;
    }

    public String getLabel() {
        return label;
    }

    public String getResolverEffect() {
        return resolverEffect;
    }

    public void setResolverEffect(String resolverEffect) {
        this.resolverEffect = resolverEffect;
    }

    public String getVerifiedEffect() {
        return verifiedEffect;
    }

    public void setVerifiedEffect(String verifiedEffect) {
        this.verifiedEffect = verifiedEffect;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public boolean isMismatch() {
        return mismatch;
    }

    public void setMismatch(boolean mismatch) {
        this.mismatch = mismatch;
    }

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    public RuleReference getDecidingRule() {
        return decidingRule;
    }

    public void setDecidingRule(RuleReference decidingRule) {
        this.decidingRule = decidingRule;
    }

    public List<RuleReference> getOverriddenRules() {
        return overriddenRules;
    }

    public String getRemediation() {
        return remediation;
    }

    public void setRemediation(String remediation) {
        this.remediation = remediation;
    }

    public String getAiExplanation() {
        return aiExplanation;
    }

    public void setAiExplanation(String aiExplanation) {
        this.aiExplanation = aiExplanation;
    }

    public boolean isEffectivelyBlocked() {
        return effectivelyBlocked;
    }

    public void setEffectivelyBlocked(boolean effectivelyBlocked) {
        this.effectivelyBlocked = effectivelyBlocked;
    }

    public String getEffectiveBlockReason() {
        return effectiveBlockReason;
    }

    public void setEffectiveBlockReason(String effectiveBlockReason) {
        this.effectiveBlockReason = effectiveBlockReason;
    }
}
