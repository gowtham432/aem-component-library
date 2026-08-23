package com.mycompany.core.pi.services.impl;

import com.mycompany.core.pi.models.AccessDiagnosisResult;
import com.mycompany.core.pi.models.PermissionEffect;
import com.mycompany.core.pi.models.PermissionResolutionResult;
import com.mycompany.core.pi.models.PrivilegeDiagnosis;
import com.mycompany.core.pi.models.RuleReference;
import com.mycompany.core.pi.services.AccessDiagnosisService;
import com.mycompany.core.pi.services.NarrativeExplanationService;
import com.mycompany.core.pi.services.PermissionVerificationService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Session;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component(service = AccessDiagnosisService.class, immediate = true)
public class AccessDiagnosisServiceImpl implements AccessDiagnosisService {

    @Reference
    private PermissionVerificationService verificationService;

    @Reference
    private NarrativeExplanationService narrativeExplanationService;

    /**
     * Package-visible so tests can wire a real (or fake) implementation
     * without an OSGi container -- the {@code @Reference} fields above are
     * only populated by SCR at runtime.
     */
    void setVerificationServiceForTests(PermissionVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    void setNarrativeExplanationServiceForTests(NarrativeExplanationService narrativeExplanationService) {
        this.narrativeExplanationService = narrativeExplanationService;
    }

    @Override
    public AccessDiagnosisResult diagnose(Session session, String userId, String path, Set<String> principalNames,
            PermissionResolutionResult permissions, Set<String> requestedPrivileges) {
        if (!permissions.isSuccess()) {
            return AccessDiagnosisResult.error(userId, path, permissions.getError());
        }

        AccessDiagnosisResult result = new AccessDiagnosisResult();
        result.setUserId(userId);
        result.setPath(path);

        Set<String> privilegesToCheck = effectivePrivileges(requestedPrivileges);
        Map<String, PermissionEffect> atPath = permissions.getPermissions().getOrDefault(path, Collections.emptyMap());

        for (Map.Entry<String, String> entry : SUPPORTED_PRIVILEGES.entrySet()) {
            String privilege = entry.getKey();
            if (!privilegesToCheck.contains(privilege)) {
                continue;
            }
            String label = entry.getValue();
            PermissionEffect effect = atPath.get(privilege);
            String verifiedEffect = verificationService.checkPrivilege(session, principalNames, path, privilege);
            result.getChecks().add(buildDiagnosis(privilege, label, effect, verifiedEffect, userId, path));
        }

        applyEffectiveAccessWarnings(result, atPath, session, principalNames, path);

        return result;
    }

    /**
     * Oak evaluates every privilege independently, but AEM/Sling resource
     * resolution requires jcr:read to reach a node at all -- so a node can be
     * "writable" per Oak yet completely invisible/unreachable in practice.
     * Cross-checks jcr:read at this path (resolving it even if the caller
     * didn't ask to see it) and flags every other ALLOWED check as
     * effectively unreachable when read itself is denied here, whatever the
     * reason. Deliberately leaves the underlying Oak verdict untouched --
     * only jcr:read being denied is the trigger, and a check that's already
     * blocked has nothing misleading to warn about.
     */
    private void applyEffectiveAccessWarnings(AccessDiagnosisResult result, Map<String, PermissionEffect> atPath,
            Session session, Set<String> principalNames, String path) {
        PrivilegeDiagnosis readCheck = findCheck(result, "jcr:read");

        boolean readBlocked;
        RuleReference readDecidingRule;
        if (readCheck != null) {
            readBlocked = readCheck.isBlocked();
            readDecidingRule = readCheck.getDecidingRule();
        } else {
            PermissionEffect readEffect = atPath.get("jcr:read");
            String verifiedReadEffect = verificationService.checkPrivilege(session, principalNames, path, "jcr:read");
            readBlocked = verifiedReadEffect != null
                    ? "DENY".equals(verifiedReadEffect)
                    : (readEffect != null && "DENY".equals(readEffect.getEffect()));
            readDecidingRule = readEffect == null ? null : readEffect.getDecidingRule();
        }

        if (!readBlocked) {
            return;
        }

        for (PrivilegeDiagnosis diagnosis : result.getChecks()) {
            if ("jcr:read".equals(diagnosis.getPrivilege()) || diagnosis.isBlocked()) {
                continue;
            }
            diagnosis.setEffectivelyBlocked(true);
            diagnosis.setEffectiveBlockReason(buildEffectiveBlockReason(diagnosis, readDecidingRule, path));
        }
    }

    private PrivilegeDiagnosis findCheck(AccessDiagnosisResult result, String privilege) {
        for (PrivilegeDiagnosis diagnosis : result.getChecks()) {
            if (privilege.equals(diagnosis.getPrivilege())) {
                return diagnosis;
            }
        }
        return null;
    }

    private String buildEffectiveBlockReason(PrivilegeDiagnosis diagnosis, RuleReference readDecidingRule, String path) {
        StringBuilder sb = new StringBuilder();
        sb.append("jcr:read is denied on ").append(path).append(". This node is not visible or resolvable to this user, ")
                .append("so ").append(diagnosis.getLabel().toLowerCase()).append(" cannot be exercised without read access.");

        if (readDecidingRule != null) {
            sb.append(" Root cause: ").append(readDecidingRule.getType()).append(" on ")
                    .append(readDecidingRule.getPrincipal()).append(" at ").append(readDecidingRule.getSetAtPath()).append(".");
            sb.append(" Fix: grant jcr:read to a group this user belongs to at ").append(readDecidingRule.getSetAtPath())
                    .append(", or remove the ").append(readDecidingRule.getType()).append(" from ")
                    .append(readDecidingRule.getPrincipal()).append(".");
        } else {
            sb.append(" No rule anywhere in this user's group memberships grants jcr:read at this path, ")
                    .append("so the repository denies it by default.");
            sb.append(" Fix: grant jcr:read to a group this user belongs to at this path.");
        }

        return sb.toString();
    }

    /**
     * Silently falls back to {@link #DEFAULT_PRIVILEGES} rather than erroring
     * out on garbage input -- an unrecognized privilege key here just means
     * the client sent something outside {@link #SUPPORTED_PRIVILEGES}.
     */
    private Set<String> effectivePrivileges(Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return DEFAULT_PRIVILEGES;
        }
        Set<String> known = new LinkedHashSet<>(requested);
        known.retainAll(SUPPORTED_PRIVILEGES.keySet());
        return known.isEmpty() ? DEFAULT_PRIVILEGES : known;
    }

    private PrivilegeDiagnosis buildDiagnosis(String privilege, String label, PermissionEffect effect, String verifiedEffect,
            String userId, String path) {
        PrivilegeDiagnosis diagnosis = new PrivilegeDiagnosis(privilege, label);
        diagnosis.setResolverEffect(effect == null ? null : effect.getEffect());
        diagnosis.setVerifiedEffect(verifiedEffect);
        diagnosis.setMismatch(effect != null && verifiedEffect != null && !verifiedEffect.equals(effect.getEffect()));

        boolean blocked = verifiedEffect != null
                ? "DENY".equals(verifiedEffect)
                : (effect != null && "DENY".equals(effect.getEffect()));
        diagnosis.setBlocked(blocked);

        if (effect != null) {
            diagnosis.setDecidingRule(effect.getDecidingRule());
            diagnosis.setInherited(effect.isInherited());
            // Which other groups' rules were shadowed only matters as a
            // troubleshooting lead when access is actually blocked -- for an
            // allowed check it's just noise (e.g. a shallower DENY that
            // never applied here, or another group's ALLOW that wasn't
            // even needed), so it's deliberately left empty otherwise.
            if (blocked) {
                diagnosis.getOverriddenRules().addAll(otherRelevantRules(effect));
            }
        }

        diagnosis.setRemediation(buildRemediation(diagnosis, blocked, effect));
        // Same reasoning: a narrated "why" is only useful when something
        // needs explaining. An allowed check gets a precise one-line fact
        // from buildRemediation() above, not a generated narrative.
        if (blocked && narrativeExplanationService != null) {
            diagnosis.setAiExplanation(narrativeExplanationService.explain(diagnosis, userId, path));
        }
        return diagnosis;
    }

    /**
     * A group's own shallower rule being shadowed by that same group's deeper
     * rule isn't actionable information for "who else is involved" -- it's
     * expected deepest-path-wins behavior for a single principal. Keep only
     * distinct other principals so the diagnosis names the groups actually
     * worth looking at.
     */
    private List<RuleReference> otherRelevantRules(PermissionEffect effect) {
        String decidingPrincipal = effect.getDecidingRule() == null ? null : effect.getDecidingRule().getPrincipal();
        List<RuleReference> relevant = new java.util.ArrayList<>();
        Set<String> seenPrincipals = new HashSet<>();
        for (RuleReference rule : effect.getOverriddenRules()) {
            if (rule.getPrincipal().equals(decidingPrincipal)) {
                continue;
            }
            if (seenPrincipals.add(rule.getPrincipal())) {
                relevant.add(rule);
            }
        }
        return relevant;
    }

    private String buildRemediation(PrivilegeDiagnosis diagnosis, boolean blocked, PermissionEffect effect) {
        RuleReference decidingRule = effect == null ? null : effect.getDecidingRule();

        // A mismatch means our simplified deepest-path/deny-wins trace and the
        // real evaluator disagree on the outcome itself -- decidingRule is the
        // resolver's guess for the OTHER (wrong) outcome, so stating it as
        // "Access granted: DENY on X" or "Blocked by an ALLOW rule on X" would
        // be self-contradictory. Say what's actually true (the verified
        // outcome) and name the rule only as an unconfirmed lead.
        if (diagnosis.isMismatch() && decidingRule != null) {
            return (blocked ? "Blocked" : "Access granted") + " (verified against the real evaluator). "
                    + "Our simplified trace expected " + decidingRule.getPrincipal() + "'s " + decidingRule.getType()
                    + " rule (set at " + decidingRule.getSetAtPath() + ") to decide this, but the repository's actual "
                    + "rule evaluation order produced the opposite result here -- treat that group as an unconfirmed "
                    + "lead, not the confirmed cause.";
        }

        if (!blocked) {
            // Case 1 -- the ACE deciding this lives right on the requested node.
            if (decidingRule != null && !diagnosis.isInherited()) {
                return "Access granted: " + decidingRule.getType() + " on " + decidingRule.getPrincipal()
                        + " (via " + via(decidingRule) + "), set at " + decidingRule.getSetAtPath() + ".";
            }
            // Case 2 -- no ACE on this exact node; the grant comes from an
            // ancestor. Oak defaults to DENY, so this is never "nothing denies
            // it either" -- name the ancestor ACE that's actually responsible.
            if (decidingRule != null) {
                return "No ACE on this node. Access inherited from " + decidingRule.getSetAtPath() + " -- "
                        + decidingRule.getType() + " " + grantedPrivilegeLabel(decidingRule, diagnosis.getPrivilege())
                        + " via " + decidingRule.getPrincipal() + " (" + via(decidingRule) + ").";
            }
            // Case 3 -- verified ALLOW but no ACE anywhere in the chain up to
            // root explains it. Oak never allows without a grant, so this is
            // a signal to investigate, not a clean result to report at face value.
            return "Access is allowed but no traceable ACE was found on this node or any ancestor. "
                    + "Oak defaults to DENY -- this result is unexpected and may indicate a system-level "
                    + "grant or an evaluation error. Investigate before trusting this result.";
        }

        if (decidingRule == null) {
            return "Blocked: no rule anywhere in this user's group memberships explicitly grants "
                    + diagnosis.getLabel().toLowerCase() + " at this path, and the repository denies by default. "
                    + "Ask an administrator to add an ALLOW rule for a group this user belongs to.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Blocked by a ").append(decidingRule.getType()).append(" rule on ")
                .append(decidingRule.getPrincipal()).append(", set at ").append(decidingRule.getSetAtPath()).append(".");
        sb.append(" This user reaches ").append(decidingRule.getPrincipal()).append(" via ").append(via(decidingRule)).append(".");

        sb.append(" To restore access: remove this user from ").append(decidingRule.getPrincipal())
                .append(", or ask an administrator to add a more specific ALLOW for a group this user belongs to at ")
                .append(diagnosis.isInherited() ? "this exact path (deeper than " + decidingRule.getSetAtPath() + ")" : "this path")
                .append(".");

        return sb.toString();
    }

    /**
     * What the ACE itself literally grants (e.g. jcr:all), which can differ
     * from the privilege actually being checked (e.g. jcr:read) -- falls back
     * to the checked privilege if the rule's own privilege list wasn't tracked.
     */
    private String grantedPrivilegeLabel(RuleReference rule, String checkedPrivilege) {
        List<String> literal = rule.getPrivileges();
        return (literal == null || literal.isEmpty()) ? checkedPrivilege : String.join(", ", literal);
    }

    private String via(RuleReference rule) {
        return String.join(" → ", rule.getVia() == null ? Arrays.asList() : rule.getVia());
    }
}
