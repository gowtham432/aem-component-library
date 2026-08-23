package com.mycompany.core.pi;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Value;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.AccessControlPolicyIterator;
import javax.jcr.security.Privilege;
import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * Backs pi.services tests with a real, full-featured in-memory Oak
 * repository (not io.wcm's lightweight jcr-mock, which does not implement
 * UserManager/AccessControlManager/JCR-SQL2) so tests exercise the actual
 * Jackrabbit behavior these services depend on. Also ships the same
 * {@code piPrincipalNameLookup} property index defined in ui.content, so
 * ACLCollectionService is tested against the same index shape used in
 * production.
 */
public abstract class PiRepositoryTestSupport {

    protected Repository repository;
    protected Session adminSession;

    @BeforeEach
    void setUpRepository() throws RepositoryException {
        repository = new Jcr().createRepository();
        adminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
        createPrincipalNameIndex();
    }

    @AfterEach
    void tearDownRepository() {
        if (adminSession != null) {
            adminSession.logout();
        }
    }

    protected UserManager userManager() throws RepositoryException {
        return ((JackrabbitSession) adminSession).getUserManager();
    }

    protected User createUser(String id) throws RepositoryException {
        User user = userManager().createUser(id, "password");
        adminSession.save();
        return user;
    }

    protected Group createGroup(String id) throws RepositoryException {
        Group group = userManager().createGroup(id);
        adminSession.save();
        return group;
    }

    protected void addMember(Group group, Authorizable member) throws RepositoryException {
        group.addMember(member);
        adminSession.save();
    }

    protected Node createPath(String path) throws RepositoryException {
        Node node = adminSession.getRootNode();
        for (String segment : path.substring(1).split("/")) {
            node = node.hasNode(segment) ? node.getNode(segment) : node.addNode(segment, "nt:unstructured");
        }
        return node;
    }

    protected void addAce(String path, Principal principal, boolean isAllow, String glob, String... privilegeNames)
            throws RepositoryException {
        createPath(path);
        AccessControlManager acm = adminSession.getAccessControlManager();

        Privilege[] privileges = new Privilege[privilegeNames.length];
        for (int i = 0; i < privilegeNames.length; i++) {
            privileges[i] = acm.privilegeFromName(privilegeNames[i]);
        }

        JackrabbitAccessControlList acl = findOrCreateAcl(acm, path);
        Map<String, Value> restrictions = new HashMap<>();
        if (glob != null) {
            restrictions.put("rep:glob", adminSession.getValueFactory().createValue(glob));
        }
        acl.addEntry(principal, privileges, isAllow, restrictions);
        acm.setPolicy(path, acl);
        adminSession.save();
    }

    private JackrabbitAccessControlList findOrCreateAcl(AccessControlManager acm, String path) throws RepositoryException {
        for (AccessControlPolicy policy : acm.getPolicies(path)) {
            if (policy instanceof JackrabbitAccessControlList) {
                return (JackrabbitAccessControlList) policy;
            }
        }
        AccessControlPolicyIterator it = acm.getApplicablePolicies(path);
        while (it.hasNext()) {
            AccessControlPolicy policy = it.nextAccessControlPolicy();
            if (policy instanceof JackrabbitAccessControlList) {
                return (JackrabbitAccessControlList) policy;
            }
        }
        throw new RepositoryException("No JackrabbitAccessControlList available at " + path);
    }

    private void createPrincipalNameIndex() throws RepositoryException {
        Node oakIndex = adminSession.getRootNode().hasNode("oak:index")
                ? adminSession.getRootNode().getNode("oak:index")
                : adminSession.getRootNode().addNode("oak:index", "nt:unstructured");
        if (oakIndex.hasNode("piPrincipalNameLookup")) {
            return;
        }
        Node index = oakIndex.addNode("piPrincipalNameLookup", "oak:QueryIndexDefinition");
        index.setProperty("type", "property");
        index.setProperty("propertyNames", new String[]{"rep:principalName"}, PropertyType.NAME);
        index.setProperty("declaringNodeTypes", new String[]{"rep:GrantACE", "rep:DenyACE"}, PropertyType.NAME);
        index.setProperty("reindex", true);
        adminSession.save();
    }
}
