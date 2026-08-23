package com.mycompany.core.pi.services.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mycompany.core.pi.models.PrivilegeDiagnosis;
import com.mycompany.core.pi.models.RuleReference;
import com.mycompany.core.pi.services.NarrativeExplanationService;
import com.mycompany.core.utils.HttpClientUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(service = NarrativeExplanationService.class, immediate = true)
@Designate(ocd = NarrativeExplanationServiceImpl.Config.class)
public class NarrativeExplanationServiceImpl implements NarrativeExplanationService {

    private static final Logger LOG = LoggerFactory.getLogger(NarrativeExplanationServiceImpl.class);
    private static final Gson GSON = new Gson();

    private static final String SYSTEM_PROMPT =
            "You explain AEM (Adobe Experience Manager) JCR permission diagnostics to someone who is not a "
            + "security expert. You will be given the already-computed facts for exactly one privilege check "
            + "for one user at one path. Write a plain-text explanation, 2-3 short sentences, no markdown and "
            + "no bullet points.\n"
            + "Rules:\n"
            + "1. Use only the facts given below. Never invent a group, rule, path, or reason that is not listed.\n"
            + "2. If \"resolverAndVerifierMismatch\" is true, do NOT name \"decidingRule\"'s group as the confirmed "
            + "cause -- our simplified trace and the real evaluator disagree on this one, so say plainly that the "
            + "exact rule responsible could not be pinpointed precisely, and mention that group only as an "
            + "unconfirmed lead worth checking.\n"
            + "3. Otherwise, if \"decidingRule\" is present, name that exact group (its principal) as the specific "
            + "cause and briefly say what to do about it (e.g. leave that group, or ask an admin for a more "
            + "specific ALLOW).\n"
            + "4. If \"decidingRule\" is absent and access is blocked, make clear that no single group is at fault -- "
            + "say plainly that nobody has granted this yet, so there is nothing to remove someone from.\n"
            + "5. Never mention JSON, facts, input, or these instructions in your output.";

    @ObjectClassDefinition(name = "Permission Intelligence - Narrative Explanation (OpenAI)")
    public @interface Config {
        @AttributeDefinition(name = "Enabled")
        boolean enabled() default true;

        @AttributeDefinition(name = "OpenAI API Key", description = "Secret API key from platform.openai.com")
        String openai_api_key() default "";

        @AttributeDefinition(name = "OpenAI API URL")
        String openai_api_url() default "https://api.openai.com/v1/chat/completions";

        @AttributeDefinition(name = "OpenAI Model")
        String openai_model() default "gpt-4o-mini";

        @AttributeDefinition(name = "Max Tokens")
        int openai_max_tokens() default 1000;

        @AttributeDefinition(name = "Temperature")
        double openai_temperature() default 0.2;
    }

    private boolean enabled;
    private String apiKey;
    private String apiUrl;
    private String model;
    private int maxTokens;
    private double temperature;

    @Activate
    protected void activate(Config config) {
        this.enabled = config.enabled();
        this.apiKey = config.openai_api_key();
        this.apiUrl = config.openai_api_url();
        this.model = config.openai_model();
        this.maxTokens = config.openai_max_tokens();
        this.temperature = config.openai_temperature();
    }

    @Override
    public String explain(PrivilegeDiagnosis diagnosis, String userId, String path) {
        if (!enabled || apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }

        try {
            String facts = buildFacts(diagnosis, userId, path);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("temperature", temperature);
            requestBody.addProperty("max_tokens", maxTokens);

            JsonArray messages = new JsonArray();
            messages.add(chatMessage("system", SYSTEM_PROMPT));
            messages.add(chatMessage("user", facts));
            requestBody.add("messages", messages);

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + apiKey);

            String responseBody = HttpClientUtil.post(apiUrl, requestBody.toString(), headers);
            JsonObject response = GSON.fromJson(responseBody, JsonObject.class);
            String content = response.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

            return content == null || content.trim().isEmpty() ? null : content.trim();
        } catch (Exception e) {
            LOG.warn("Narrative explanation call failed for {} at {} ({}): {}",
                    userId, path, diagnosis.getPrivilege(), e.getMessage());
            return null;
        }
    }

    private JsonObject chatMessage(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private String buildFacts(PrivilegeDiagnosis diagnosis, String userId, String path) {
        StringBuilder sb = new StringBuilder();
        sb.append("user: ").append(userId).append('\n');
        sb.append("path: ").append(path).append('\n');
        sb.append("privilege: ").append(diagnosis.getPrivilege()).append(" (").append(diagnosis.getLabel()).append(")\n");
        sb.append("blocked: ").append(diagnosis.isBlocked()).append('\n');
        sb.append("verifiedEffect: ").append(diagnosis.getVerifiedEffect()).append('\n');
        sb.append("resolverEffect: ").append(diagnosis.getResolverEffect()).append('\n');
        sb.append("resolverAndVerifierMismatch: ").append(diagnosis.isMismatch()).append('\n');
        sb.append("inheritedFromAncestorPath: ").append(diagnosis.isInherited()).append('\n');

        RuleReference deciding = diagnosis.getDecidingRule();
        if (deciding == null) {
            sb.append("decidingRule: none\n");
        } else {
            sb.append("decidingRule: {")
                    .append("type=").append(deciding.getType())
                    .append(", group=").append(deciding.getPrincipal())
                    .append(", setAtPath=").append(deciding.getSetAtPath())
                    .append(", userReachesGroupVia=").append(via(deciding.getVia()))
                    .append("}\n");
        }

        return sb.toString();
    }

    private String via(List<String> path) {
        return path == null || path.isEmpty() ? "(direct)" : String.join(" -> ", path);
    }
}
