package com.mycompany.core.pi.servlets;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mycompany.core.pi.services.AccessDiagnosisService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Map;

/**
 * GET /bin/permission-intelligence/privileges.json
 *
 * Lists the privileges the diagnosis servlet knows how to check, so the
 * frontend's dropdown is built from the same vocabulary the backend
 * validates against instead of a hand-maintained duplicate list.
 */
@Component(service = Servlet.class, property = {
        "sling.servlet.paths=/bin/permission-intelligence/privileges",
        "sling.servlet.methods=GET"
})
public class PermissionIntelligencePrivilegesServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;
    private static final Gson GSON = new Gson();

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");

        JsonArray privileges = new JsonArray();
        for (Map.Entry<String, String> entry : AccessDiagnosisService.SUPPORTED_PRIVILEGES.entrySet()) {
            JsonObject privilege = new JsonObject();
            privilege.addProperty("value", entry.getKey());
            privilege.addProperty("label", entry.getValue());
            privilege.addProperty("default", AccessDiagnosisService.DEFAULT_PRIVILEGES.contains(entry.getKey()));
            privileges.add(privilege);
        }

        JsonObject envelope = new JsonObject();
        envelope.addProperty("status", "success");
        envelope.add("privileges", privileges);

        response.setStatus(200);
        response.getWriter().write(GSON.toJson(envelope));
    }
}
