package com.mycompany.core.pi.services.impl;

import com.mycompany.core.pi.models.ACEInfo;
import com.mycompany.core.pi.models.ACLCollectionResult;
import com.mycompany.core.pi.models.GroupInfo;
import com.mycompany.core.pi.models.GroupResolutionResult;
import com.mycompany.core.pi.models.PermissionEffect;
import com.mycompany.core.pi.models.PermissionResolutionResult;
import com.mycompany.core.pi.models.RuleReference;
import com.mycompany.core.pi.services.PermissionResolverService;
import com.mycompany.core.pi.util.PathUtils;
import org.osgi.service.component.annotations.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * See {@link PermissionResolverService} for the important caveat: this
 * builds a human-readable trace using the standard "deepest path wins, deny
 * beats allow at the same path" mental model, not Oak's exact evaluation
 * order. Treat its output as an explanation, verified elsewhere against the
 * real evaluator.
 */
@Component(service = PermissionResolverService.class, immediate = true)
public class PermissionResolverServiceImpl implements PermissionResolverService {

    @Override
    public PermissionResolutionResult resolvePermissions(GroupResolutionResult groups, ACLCollectionResult acls) {
        if (!groups.isSuccess() || !acls.isSuccess()) {
            return PermissionResolutionResult.error(groups.getUserId(), "Cannot resolve permissions: upstream data unavailable");
        }

        PermissionResolutionResult result = new PermissionResolutionResult();
        result.setUserId(groups.getUserId());

        Set<String> userPrincipals = new HashSet<>(groups.getAllGroups().keySet());
        userPrincipals.add(groups.getUserId());

        Map<String, List<ACEInfo>> aclsByPath = acls.getAclsByPath();

        // A path with no ACE of its own (e.g. a leaf DAM asset node) never
        // appears as a key in aclsByPath, but it can still resolve to ALLOW
        // via an ancestor's rule -- resolveOne() already walks ancestors for
        // whatever path it's given. Without including the actual diagnosis
        // target here, that path would simply never get resolved at all,
        // leaving the caller with no decidingRule to explain an inherited
        // grant (or deny) with.
        Set<String> pathsToResolve = new HashSet<>(aclsByPath.keySet());
        if (acls.getRootPath() != null && !acls.getRootPath().trim().isEmpty()) {
            pathsToResolve.add(acls.getRootPath());
        }

        List<String> sortedPaths = pathsToResolve.stream()
                .sorted(Comparator.comparingInt(this::depth).thenComparing(Comparator.naturalOrder()))
                .collect(Collectors.toList());

        Set<String> allPrivileges = aclsByPath.values().stream()
                .flatMap(List::stream)
                .flatMap(ace -> ace.getPrivileges().stream())
                .collect(Collectors.toCollection(TreeSet::new));

        for (String path : sortedPaths) {
            Map<String, PermissionEffect> perPrivilege = new LinkedHashMap<>();
            for (String privilege : allPrivileges) {
                PermissionEffect effect = resolveOne(path, privilege, userPrincipals, aclsByPath, groups);
                if (effect != null) {
                    perPrivilege.put(privilege, effect);
                    tally(result, effect);
                }
            }
            if (!perPrivilege.isEmpty()) {
                result.getPermissions().put(path, perPrivilege);
            }
        }

        result.setTotalPaths(result.getPermissions().size());
        return result;
    }

    private void tally(PermissionResolutionResult result, PermissionEffect effect) {
        if ("DENY".equals(effect.getEffect())) {
            result.incrementDenies();
        } else {
            result.incrementAllows();
        }
        if (effect.getAnomaly() != null) {
            result.incrementConflicts();
        }
    }

    /**
     * Walks ancestor-or-self content paths of {@code path}, deepest first,
     * looking for the first level with at least one applicable ACE (deeper
     * path wins). At that level, deny beats allow. Any ACE at a shallower
     * level that would otherwise have applied is recorded as overridden.
     */
    private PermissionEffect resolveOne(String path, String privilege, Set<String> userPrincipals,
            Map<String, List<ACEInfo>> aclsByPath, GroupResolutionResult groups) {

        List<String> ancestorPaths = aclsByPath.keySet().stream()
                .filter(contentPath -> PathUtils.isAncestorOrSelf(contentPath, path))
                .sorted(Comparator.comparingInt(this::depth).reversed())
                .collect(Collectors.toList());

        for (int i = 0; i < ancestorPaths.size(); i++) {
            String contentPath = ancestorPaths.get(i);
            List<ACEInfo> candidates = applicableAces(aclsByPath.get(contentPath), contentPath, path, privilege, userPrincipals);
            if (candidates.isEmpty()) {
                continue;
            }

            List<ACEInfo> denies = candidates.stream().filter(a -> "DENY".equals(a.getType())).collect(Collectors.toList());
            List<ACEInfo> allows = candidates.stream().filter(a -> "ALLOW".equals(a.getType())).collect(Collectors.toList());
            boolean denyWins = !denies.isEmpty();
            ACEInfo decidingAce = denyWins ? denies.get(0) : allows.get(0);
            List<ACEInfo> beatenAtThisLevel = denyWins ? allows : Collections.emptyList();

            PermissionEffect effect = new PermissionEffect(
                    denyWins ? "DENY" : "ALLOW",
                    toRuleReference(decidingAce, groups),
                    !contentPath.equals(path));

            for (ACEInfo beaten : beatenAtThisLevel) {
                effect.getOverriddenRules().add(toRuleReference(beaten, groups));
            }
            for (int j = i + 1; j < ancestorPaths.size(); j++) {
                String shallowerPath = ancestorPaths.get(j);
                for (ACEInfo shadowed : applicableAces(aclsByPath.get(shallowerPath), shallowerPath, path, privilege, userPrincipals)) {
                    effect.getOverriddenRules().add(toRuleReference(shadowed, groups));
                }
            }

            if (!effect.getOverriddenRules().isEmpty()) {
                effect.setAnomaly(!beatenAtThisLevel.isEmpty() ? "CONFLICT" : "OVERRIDE");
            }

            return effect;
        }

        return null;
    }

    private List<ACEInfo> applicableAces(List<ACEInfo> aces, String contentPath, String targetPath,
            String privilege, Set<String> userPrincipals) {
        return aces.stream()
                .filter(ace -> userPrincipals.contains(ace.getPrincipal()))
                .filter(ace -> ace.getPrivileges().contains(privilege))
                .filter(ace -> matchesGlob(contentPath, ace.getRestrictions().get("rep:glob"), targetPath))
                .collect(Collectors.toList());
    }

    private RuleReference toRuleReference(ACEInfo ace, GroupResolutionResult groups) {
        List<String> via;
        if (ace.getPrincipal().equals(groups.getUserId())) {
            via = Collections.singletonList(groups.getUserId());
        } else {
            GroupInfo info = groups.getAllGroups().get(ace.getPrincipal());
            via = (info == null || info.getMembershipPaths().isEmpty())
                    ? Collections.singletonList(ace.getPrincipal())
                    : info.getMembershipPaths().stream().min(Comparator.comparingInt(List::size)).orElse(Collections.emptyList());
        }
        return new RuleReference(ace.getPrincipal(), ace.getType(), ace.getContentPath(),
                ace.getRestrictions().get("rep:glob"), via, ace.getLiteralPrivileges());
    }

    /**
     * Doc-specified subset of Jackrabbit rep:glob semantics: null applies to
     * the node and all descendants, "" restricts to the node itself, and "*"
     * matches exactly one path segment. Multi-segment "**" wildcards are not
     * part of this subset; the hasPrivileges cross-check catches any case
     * this simplification gets wrong.
     */
    private boolean matchesGlob(String acePath, String glob, String targetPath) {
        if (glob == null) {
            return true;
        }
        String relative;
        if (targetPath.equals(acePath)) {
            relative = "";
        } else if ("/".equals(acePath)) {
            relative = targetPath.substring(1);
        } else {
            relative = targetPath.substring(acePath.length() + 1);
        }
        if (glob.isEmpty()) {
            return relative.isEmpty();
        }
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            regex.append(c == '*' ? "[^/]+" : Pattern.quote(String.valueOf(c)));
        }
        return relative.matches(regex.toString());
    }

    private int depth(String path) {
        return "/".equals(path) ? 0 : path.split("/").length - 1;
    }
}
