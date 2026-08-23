package com.mycompany.core.pi.services;

import com.mycompany.core.pi.models.ACLCollectionResult;
import com.mycompany.core.pi.models.GroupResolutionResult;
import com.mycompany.core.pi.models.PermissionResolutionResult;

/**
 * Resolves effective ALLOW/DENY per path per privilege from a user's group
 * graph and the ACEs collected for it, with a human-readable trace of which
 * ACE decided each outcome and which ACEs it beat.
 *
 * <p>This mirrors the standard mental model of Jackrabbit ACE evaluation
 * (deepest applicable path wins; at the same path, deny beats allow; glob
 * restrictions narrow which paths an ACE applies to) but is <b>not</b> a
 * byte-for-byte reimplementation of Oak's actual evaluation order, which for
 * same-node ACEs also depends on the order entries were originally added and
 * an implementation-defined principal ordering. The result of this resolver
 * should be treated as an explanation, not as ground truth -- ground truth
 * for the final ALLOW/DENY comes from a separate cross-check against the
 * real evaluator (see the "resolverMismatch" step of the build).
 */
public interface PermissionResolverService {

    PermissionResolutionResult resolvePermissions(GroupResolutionResult groups, ACLCollectionResult acls);
}
