package com.mycompany.core.pi.services.impl;

import com.mycompany.core.pi.models.ACEInfo;
import com.mycompany.core.pi.models.ACLCollectionResult;
import com.mycompany.core.pi.services.ACLCollectionService;
import com.mycompany.core.pi.util.PathUtils;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.security.AccessControlException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.Privilege;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Looks up ACEs by principal name via JCR-SQL2 rather than walking the
 * content tree. Relies on the {@code piPrincipalNameLookup} Oak property
 * index (shipped in ui.content under /oak:index) to keep this an indexed
 * lookup instead of a full repository traversal.
 *
 * <p>Privilege names on each ACE are expanded to include every aggregate
 * privilege they imply (e.g. a {@code jcr:all} ACE also gets {@code jcr:read},
 * {@code jcr:write}, ... added to its privilege list) so downstream literal
 * privilege-name matching in PermissionResolverService correctly recognizes
 * that a broad DENY covers a specific privilege someone is asking about.
 */
@Component(service = ACLCollectionService.class, immediate = true)
public class ACLCollectionServiceImpl implements ACLCollectionService {

    private static final Logger LOG = LoggerFactory.getLogger(ACLCollectionServiceImpl.class);

    @Override
    public ACLCollectionResult collectACLs(Session session, Set<String> principals, String rootPath) {
        ACLCollectionResult result = new ACLCollectionResult();
        result.setRootPath(rootPath);

        if (principals == null || principals.isEmpty()) {
            return result;
        }

        try {
            QueryManager queryManager = session.getWorkspace().getQueryManager();
            String sql2 = "SELECT * FROM [rep:ACE] WHERE [rep:principalName] IN ("
                    + principals.stream().map(this::quote).collect(Collectors.joining(","))
                    + ")";
            Query query = queryManager.createQuery(sql2, Query.JCR_SQL2);
            QueryResult queryResult = query.execute();
            NodeIterator nodes = queryResult.getNodes();

            while (nodes.hasNext()) {
                Node aceNode = nodes.nextNode();
                ACEInfo ace = toACEInfo(aceNode);
                if (ace == null) {
                    continue;
                }
                if (rootPath != null && !rootPath.isEmpty() && !"/".equals(rootPath)
                        && !PathUtils.isAncestorOrSelf(ace.getContentPath(), rootPath)
                        && !PathUtils.isAncestorOrSelf(rootPath, ace.getContentPath())) {
                    continue;
                }
                result.addACE(ace);
            }

            return result;
        } catch (RepositoryException e) {
            LOG.error("Failed to collect ACLs for rootPath '{}': {}", rootPath, e.getMessage(), e);
            return ACLCollectionResult.error(rootPath, "Repository error: " + e.getMessage());
        }
    }

    private ACEInfo toACEInfo(Node aceNode) {
        try {
            if (!aceNode.hasProperty("rep:principalName")) {
                return null;
            }
            String principal = aceNode.getProperty("rep:principalName").getString();
            String type = "rep:DenyACE".equals(aceNode.getPrimaryNodeType().getName()) ? "DENY" : "ALLOW";

            List<String> literalPrivileges = new ArrayList<>();
            if (aceNode.hasProperty("rep:privileges")) {
                for (Value value : aceNode.getProperty("rep:privileges").getValues()) {
                    literalPrivileges.add(value.getString());
                }
            }
            List<String> privileges = expandAggregatePrivileges(aceNode, literalPrivileges);

            Node contentNode = aceNode.getParent().getParent();
            ACEInfo ace = new ACEInfo(principal, type, privileges, literalPrivileges, aceNode.getPath(), contentNode.getPath());

            if (aceNode.hasNode("rep:restrictions")) {
                Node restrictionsNode = aceNode.getNode("rep:restrictions");
                PropertyIterator props = restrictionsNode.getProperties();
                while (props.hasNext()) {
                    Property property = props.nextProperty();
                    if ("jcr:primaryType".equals(property.getName())) {
                        continue;
                    }
                    ace.getRestrictions().put(property.getName(), propertyValueToString(property));
                }
            }

            return ace;
        } catch (RepositoryException e) {
            LOG.warn("Skipping unreadable ACE node: {}", e.getMessage());
            return null;
        }
    }

    private List<String> expandAggregatePrivileges(Node aceNode, List<String> literalNames) throws RepositoryException {
        Set<String> expanded = new LinkedHashSet<>(literalNames);
        AccessControlManager acm = aceNode.getSession().getAccessControlManager();
        for (String name : literalNames) {
            try {
                collectAggregates(acm.privilegeFromName(name), expanded);
            } catch (AccessControlException e) {
                LOG.warn("Unknown privilege name '{}' on ACE {}, keeping it as a literal only", name, aceNode.getPath());
            }
        }
        return new ArrayList<>(expanded);
    }

    private void collectAggregates(Privilege privilege, Set<String> acc) {
        for (Privilege sub : privilege.getAggregatePrivileges()) {
            if (acc.add(sub.getName())) {
                collectAggregates(sub, acc);
            }
        }
    }

    private String propertyValueToString(Property property) throws RepositoryException {
        if (!property.isMultiple()) {
            return property.getString();
        }
        List<String> values = new ArrayList<>();
        for (Value value : property.getValues()) {
            values.add(value.getString());
        }
        return String.join(",", values);
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
