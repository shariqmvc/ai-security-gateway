package com.ai.gateway.routing;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.routing.constraint.CandidateConstraintEvaluator;
import com.ai.gateway.routing.engine.CandidateEligibilityFilter;
import com.ai.gateway.routing.engine.CandidateModelResolver;
import com.ai.gateway.routing.engine.CandidateProviderResolver;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.policy.RoutingPolicy;
import com.ai.gateway.routing.policy.RoutingPolicyService;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(3)
@RequiredArgsConstructor
public class PolicyBasedRoutingStrategy
        implements RoutingStrategyHandler {

    private final RoutingPolicyService routingPolicyService;

    private final ProviderModelRegistryService
            providerModelRegistryService;

    private final CandidateProviderResolver
            candidateProviderResolver;

    private final CandidateModelResolver
            candidateModelResolver;

    private final CandidateEligibilityFilter
            candidateEligibilityFilter;

    private final CandidateConstraintEvaluator
            candidateConstraintEvaluator;

    @Override
    public boolean supports(
            RoutingContext context) {

        if (context == null
                || context.request() == null
                || context.authenticationContext() == null) {

            return false;
        }

        /*
         * Policy-based routing applies only when the caller
         * has not explicitly selected a provider or model.
         *
         * Explicit provider/model routing remains higher priority.
         */
        return context.request().getProvider() == null
                && (context.request().getModel() == null
                || context.request().getModel().isBlank());
    }

    @Override
    public RoutingDecision route(
            RoutingContext context) {

        RoutingPolicy policy =
                routingPolicyService.resolve(
                        context.request(),
                        context.authenticationContext());

        if (policy == null) {
            throw new BusinessException(
                    "Routing policy could not be resolved.");
        }

        if (!policy.enabled()) {
            throw new BusinessException(
                    "Routing policy is disabled.");
        }

        /*
         * Policy contract validation must happen BEFORE
         * candidate resolution.
         *
         * This preserves the semantic distinction between:
         *
         * 1. invalid policy configuration
         * 2. valid policy with no available candidates
         */
        Provider preferredProvider =
                policy.preferredProvider();

        String preferredModel =
                policy.preferredModel();

        if (preferredProvider == null) {
            throw new BusinessException(
                    "Routing policy does not define a provider.");
        }

        if (preferredModel == null
                || preferredModel.isBlank()) {

            throw new BusinessException(
                    "Routing policy does not define a model.");
        }

        if (!policy.allowsProvider(
                preferredProvider)) {

            throw new BusinessException(
                    "Provider "
                            + preferredProvider
                            + " is not allowed by routing policy.");
        }

        if (!policy.allowsModel(
                preferredModel)) {

            throw new BusinessException(
                    "Model "
                            + preferredModel
                            + " is not allowed by routing policy.");
        }

        /*
         * 6.5 Candidate Provider Resolution
         */
        List<Provider> providers =
                candidateProviderResolver.resolve(policy);

        if (providers == null
                || providers.isEmpty()) {

            throw new BusinessException(
                    "No eligible provider is available for routing.");
        }

        /*
         * 6.5 Candidate Model Resolution
         *
         * Models are resolved per provider so that
         * invalid provider/model combinations can never
         * be constructed.
         */
        List<RoutingCandidate> candidates =
                buildCandidates(
                        providers,
                        policy);

        if (candidates.isEmpty()) {

            throw new BusinessException(
                    "No eligible model is available for routing.");
        }

        /*
         * 6.5.3 Candidate Eligibility Filtering
         */
        List<RoutingCandidate> eligibleCandidates =
                candidateEligibilityFilter.filter(
                        candidates,
                        policy);

        if (eligibleCandidates == null
                || eligibleCandidates.isEmpty()) {

            throw new BusinessException(
                    "No eligible routing candidate is available.");
        }

        /*
         * 6.5.4 Hard Constraint Evaluation
         *
         * Eligibility filtering answers whether a candidate is
         * visible to the routing policy. Hard constraints are the
         * deterministic safety/availability gate immediately before
         * scoring. A candidate that fails any mandatory constraint
         * must never reach candidate scoring or selection.
         */
        List<RoutingCandidate> constraintEligibleCandidates =
                candidateConstraintEvaluator.filter(
                        eligibleCandidates,
                        policy);

        if (constraintEligibleCandidates == null
                || constraintEligibleCandidates.isEmpty()) {

            throw new BusinessException(
                    "No routing candidate satisfies the required hard constraints.");
        }

        /*
         * Select the preferred provider/model pair if it
         * survived candidate generation and eligibility.
         *
         * Otherwise use the first eligible candidate.
         */
        RoutingCandidate selected =
                selectCandidate(
                        policy,
                        constraintEligibleCandidates);

        /*
         * Final registry validation.
         */
        providerModelRegistryService.requireProvider(
                selected.provider());

        providerModelRegistryService.requireModel(
                selected.provider(),
                selected.model());

        return new RoutingDecision(
                selected.provider(),
                selected.model(),
                RoutingStrategy.POLICY_BASED);
    }

    private List<RoutingCandidate> buildCandidates(
            List<Provider> providers,
            RoutingPolicy policy) {

        List<RoutingCandidate> candidates =
                new ArrayList<>();

        for (Provider provider : providers) {

            if (provider == null) {
                continue;
            }

            List<String> models =
                    candidateModelResolver.resolve(
                            provider,
                            policy);

            if (models == null
                    || models.isEmpty()) {
                continue;
            }

            for (String model : models) {

                if (model == null
                        || model.isBlank()) {
                    continue;
                }

                candidates.add(
                        new RoutingCandidate(
                                provider,
                                model));
            }
        }

        return candidates;
    }

    private RoutingCandidate selectCandidate(
            RoutingPolicy policy,
            List<RoutingCandidate> candidates) {

        Provider preferredProvider =
                policy.preferredProvider();

        String preferredModel =
                policy.preferredModel();

        /*
         * Exact preferred provider/model pair wins if
         * it is present in the eligible candidate set.
         */
        if (preferredProvider != null
                && preferredModel != null
                && !preferredModel.isBlank()) {

            for (RoutingCandidate candidate : candidates) {

                if (preferredProvider.equals(
                        candidate.provider())
                        && preferredModel.equals(
                        candidate.model())) {

                    return candidate;
                }
            }
        }

        /*
         * No scoring/ranking yet.
         *
         * Deterministic first eligible candidate.
         */
        return candidates.get(0);
    }
}