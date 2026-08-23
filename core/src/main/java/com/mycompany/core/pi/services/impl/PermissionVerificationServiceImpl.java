package com.mycompany.core.pi.services.impl;

import com.mycompany.core.pi.models.PermissionEffect;
import com.mycompany.core.pi.models.PermissionResolutionResult;
import com.mycompany.core.pi.services.PermissionVerificationService;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlManager;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import java.security.Principal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component(service = PermissionVerificationService.class, immediate = true)
public class PermissionVerificationServiceImpl implements PermissionVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionVerificationServiceImpl.class);

    @Override
    public void verify(Session session, Set<String> principalNames, PermissionResolutionResult result) {
        try {
            JackrabbitAccessControlManager acm = jackrabbitAcm(session);
            Set<Principal> principals = resolvePrincipals(session, principalNames);
            for (Map.Entry<String, Map<String, PermissionEffect>> pathEntry : result.getPermissions().entrySet()) {
                String path = pathEntry.getKey();
                for (Map.Entry<String, PermissionEffect> privEntry : pathEntry.getValue().entrySet()) {
                    verifyOne(acm, principals, path, privEntry.getKey(), privEntry.getValue());
                }
            }
            result.setVerified(true);
        } catch (RepositoryException e) {
            result.setVerificationError("Verification unavailable: " + e.getMessage());
            LOG.warn("Permission verification failed: {}", e.getMessage());
        }
    }

    @Override
    public String checkPrivilege(Session session, Set<String> principalNames, String path, String privilegeName) {
        try {
            JackrabbitAccessControlManager acm = jackrabbitAcm(session);
            Set<Principal> principals = resolvePrincipals(session, principalNames);
            return hasPrivilege(acm, principals, path, privilegeName);
        } catch (RepositoryException e) {
            LOG.warn("Could not check {} at {}: {}", privilegeName, path, e.getMessage());
            return null;
        }
    }

    private void verifyOne(JackrabbitAccessControlManager acm, Set<Principal> principals, String path,
            String privilegeName, PermissionEffect effect) {
        String verifiedEffect = hasPrivilege(acm, principals, path, privilegeName);
        if (verifiedEffect == null) {
            return;
        }
        effect.setVerifiedEffect(verifiedEffect);
        if (!verifiedEffect.equals(effect.getEffect())) {
            effect.setResolverMismatch(true);
        }
    }

    private JackrabbitAccessControlManager jackrabbitAcm(Session session) throws RepositoryException {
        AccessControlManager acm = session.getAccessControlManager();
        if (!(acm instanceof JackrabbitAccessControlManager)) {
            throw new RepositoryException("AccessControlManager does not support principal-set privilege checks");
        }
        return (JackrabbitAccessControlManager) acm;
    }

    private Set<Principal> resolvePrincipals(Session session, Set<String> principalNames) throws RepositoryException {
        if (!(session instanceof JackrabbitSession)) {
            throw new RepositoryException("Session is not a JackrabbitSession");
        }
        PrincipalManager principalManager = ((JackrabbitSession) session).getPrincipalManager();
        Set<Principal> principals = new HashSet<>();
        for (String name : principalNames) {
            Principal principal = principalManager.getPrincipal(name);
            if (principal != null) {
                principals.add(principal);
            }
        }
        return principals;
    }

    private String hasPrivilege(JackrabbitAccessControlManager acm, Set<Principal> principals, String path, String privilegeName) {
        try {
            Privilege privilege = acm.privilegeFromName(privilegeName);
            return acm.hasPrivileges(path, principals, new Privilege[]{privilege}) ? "ALLOW" : "DENY";
        } catch (RepositoryException e) {
            LOG.warn("Could not check {} at {}: {}", privilegeName, path, e.getMessage());
            return null;
        }
    }
}
