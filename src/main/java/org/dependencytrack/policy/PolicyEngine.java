/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.dependencytrack.policy;

import alpine.logging.Logger;
import org.dependencytrack.model.Component;
import org.dependencytrack.model.Policy;
import org.dependencytrack.model.PolicyCondition;
import org.dependencytrack.model.PolicyViolation;
import org.dependencytrack.model.Project;
import org.dependencytrack.persistence.QueryManager;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * A lightweight policy engine that evaluates a list of components against
 * all defined policies. Each policy is evaluated using individual policy
 * evaluators. Additional evaluators can be easily added in the future.
 *
 * @author Steve Springett
 * @since 4.0.0
 */
public class PolicyEngine {

    private static final Logger LOGGER = Logger.getLogger(PolicyEngine.class);

    private final List<PolicyEvaluator> evaluators = new ArrayList<>();

    public PolicyEngine() {
        evaluators.add(new CoordinatesPolicyEvaluator());
        evaluators.add(new LicenseGroupPolicyEvaluator());
        evaluators.add(new LicensePolicyEvaluator());
        evaluators.add(new PackageURLPolicyEvaluator());
        evaluators.add(new CpePolicyEvaluator());
        evaluators.add(new SwidTagIdPolicyEvaluator());
    }

    public void evaluate(List<Component> components) {
        LOGGER.info("Evaluating " + components.size() + " component(s) against applicable policies");
        try (final QueryManager qm = new QueryManager()) {
            final List<Policy> policies = qm.getAllPolicies();
            for (final Component component: components) {
                for (final Policy policy : policies) {
                    if (policy.isGlobal() || isPolicyAssignedToProject(policy, component.getProject())) {
                        LOGGER.debug("Evaluating component (" + component.getUuid() +") against policy (" + policy.getUuid() + ")");
                        final List<PolicyConditionViolation> violations = new ArrayList<>();
                        for (final PolicyEvaluator evaluator : evaluators) {
                            evaluate(evaluator, policy, component, violations);
                        }
                        if (Policy.Operator.ANY == policy.getOperator()) {
                            if (violations.size() > 0) {
                                createPolicyViolations(qm, violations);
                            }
                        } else if (Policy.Operator.ALL == policy.getOperator()) {
                            if (violations.size() == policy.getPolicyConditions().size()) {
                                createPolicyViolations(qm, violations);
                            }
                        }
                    }
                }
            }
        }
        LOGGER.info("Policy analysis complete");
    }

    private boolean isPolicyAssignedToProject(Policy policy, Project project) {
        if (policy.getProjects() == null || policy.getProjects().size() == 0) {
            return false;
        }
        return policy.getProjects().stream().anyMatch(p -> p.getId() == project.getId());
    }

    private void evaluate(final PolicyEvaluator evaluator, final Policy policy, final Component component, final List<PolicyConditionViolation> violations) {
        final Optional<PolicyConditionViolation> optional = evaluator.evaluate(policy, component);
        optional.ifPresent(violations::add);
    }

    private void createPolicyViolations(final QueryManager qm, final List<PolicyConditionViolation> pcvList) {
        for (PolicyConditionViolation pcv: pcvList) {
            final PolicyViolation pv = new PolicyViolation();
            pv.setComponent(pcv.getComponent());
            pv.setPolicyCondition(pcv.getPolicyCondition());
            pv.setType(determineViolationType(pcv.getPolicyCondition().getSubject()));
            pv.setTimestamp(new Date());
            qm.addPolicyViolationIfNotExist(pv);
            // TODO: Create notifications (NotificationUtil) if the policy did not previously exist.
        }
    }

    private PolicyViolation.Type determineViolationType(final PolicyCondition.Subject subject) {
        switch(subject) {
            case COORDINATES:
            case PACKAGE_URL:
            case CPE:
            case SWID_TAGID:
                return PolicyViolation.Type.COMPONENT_METADATA;
            case LICENSE:
            case LICENSE_GROUP:
                return PolicyViolation.Type.LICENSE;
        }
        return null;
    }
}