package com.mycompany.core.pi.services;

import com.mycompany.core.pi.models.PrivilegeDiagnosis;

/**
 * Turns one already-computed {@link PrivilegeDiagnosis} into a short, plain-English
 * paragraph naming the specific group responsible (or making clear that no single
 * group is to blame). The LLM only rephrases facts the resolver and verifier have
 * already proven -- it is never asked to determine the permission outcome itself,
 * so it cannot invent a wrong cause.
 */
public interface NarrativeExplanationService {

    /**
     * @return a short explanation, or {@code null} if the service is unconfigured,
     *         disabled, or the call failed -- callers must fall back to the
     *         deterministic {@link PrivilegeDiagnosis#getRemediation()} text in that case.
     */
    String explain(PrivilegeDiagnosis diagnosis, String userId, String path);
}
