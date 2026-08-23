package com.mycompany.core.pi.servlets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mycompany.core.pi.models.AccessDiagnosisResult;
import com.mycompany.core.pi.models.ACLCollectionResult;
import com.mycompany.core.pi.models.GroupResolutionResult;
import com.mycompany.core.pi.models.PermissionResolutionResult;
import com.mycompany.core.pi.services.AccessDiagnosisService;
import com.mycompany.core.pi.services.ACLCollectionService;
import com.mycompany.core.pi.services.GroupResolutionService;
import com.mycompany.core.pi.services.PermissionResolverService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * GET /bin/permission-intelligence.json?userId=...&amp;path=...
 *
 * Answers one question: why can (or can't) this user read/edit/fully access
 * this exact path, and which specific group is responsible. Runs as the
 * requesting user's own session (no service user) so results never exceed
 * what the person running the tool is themselves permitted to see in
 * useradmin/CRXDE.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.paths=/bin/permission-intelligence",
        "sling.servlet.methods=GET"
})
public class PermissionIntelligenceServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(PermissionIntelligenceServlet.class);
    private static final Gson GSON = new Gson();

    @Reference
    private GroupResolutionService groupResolutionService;

    @Reference
    private ACLCollectionService aclCollectionService;

    @Reference
    private PermissionResolverService permissionResolverService;

    @Reference
    private AccessDiagnosisService accessDiagnosisService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");

        long start = System.currentTimeMillis();
        String userId = request.getParameter("userId");
        String path = request.getParameter("path");

        if (userId == null || userId.trim().isEmpty()) {
            sendError(response, 400, "Missing required parameter: userId");
            return;
        }
        if (path == null || path.trim().isEmpty()) {
            sendError(response, 400, "Missing required parameter: path");
            return;
        }
        if (!path.startsWith("/")) {
            sendError(response, 400, "path must be an absolute JCR path starting with '/'");
            return;
        }

        Session session = request.getResourceResolver().adaptTo(Session.class);
        if (session == null) {
            sendError(response, 500, "No JCR session available for the current request");
            return;
        }

        String trimmedUserId = userId.trim();
        String trimmedPath = path.trim();

        try {
            GroupResolutionResult groups = groupResolutionService.resolveGroups(session, trimmedUserId);
            if (!groups.isSuccess()) {
                sendError(response, 404, groups.getError());
                return;
            }

            Set<String> principals = new HashSet<>(groups.getAllGroups().keySet());
            principals.add(trimmedUserId);
            ACLCollectionResult acls = aclCollectionService.collectACLs(session, principals, trimmedPath);
            if (!acls.isSuccess()) {
                sendError(response, 500, acls.getError());
                return;
            }

            PermissionResolutionResult permissions = permissionResolverService.resolvePermissions(groups, acls);
            if (!permissions.isSuccess()) {
                sendError(response, 500, permissions.getError());
                return;
            }

            Set<String> requestedPrivileges = parsePrivileges(request.getParameter("privileges"));
            AccessDiagnosisResult diagnosis = accessDiagnosisService.diagnose(session, trimmedUserId, trimmedPath, principals,
                    permissions, requestedPrivileges);
            if (!diagnosis.isSuccess()) {
                sendError(response, 500, diagnosis.getError());
                return;
            }

            JsonObject envelope = new JsonObject();
            envelope.addProperty("status", "success");
            envelope.addProperty("timestamp", Instant.now().toString());
            envelope.addProperty("executionTimeMs", System.currentTimeMillis() - start);
            envelope.addProperty("userId", trimmedUserId);
            envelope.addProperty("totalGroups", groups.getAllGroups().size());
            envelope.add("diagnosis", GSON.toJsonTree(diagnosis));

            response.setStatus(200);
            response.getWriter().write(GSON.toJson(envelope));
        } catch (Exception e) {
            LOG.error("PermissionIntelligenceServlet error for userId '{}', path '{}': {}", trimmedUserId, trimmedPath, e.getMessage(), e);
            sendError(response, 500, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Anything the client sends that isn't a recognized key in
     * {@link AccessDiagnosisService#SUPPORTED_PRIVILEGES} is dropped rather
     * than rejected -- {@link AccessDiagnosisService} falls back to its
     * defaults when nothing recognized is left.
     */
    private Set<String> parsePrivileges(String param) {
        Set<String> requested = new LinkedHashSet<>();
        if (param == null || param.trim().isEmpty()) {
            return requested;
        }
        for (String raw : param.split(",")) {
            String privilege = raw.trim();
            if (!privilege.isEmpty() && AccessDiagnosisService.SUPPORTED_PRIVILEGES.containsKey(privilege)) {
                requested.add(privilege);
            } else if (!privilege.isEmpty()) {
                LOG.warn("Ignoring unrecognized privilege '{}' in request", privilege);
            }
        }
        return requested;
    }

    private void sendError(SlingHttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        JsonObject err = new JsonObject();
        err.addProperty("status", "error");
        err.addProperty("message", message);
        response.getWriter().write(GSON.toJson(err));
    }
}
