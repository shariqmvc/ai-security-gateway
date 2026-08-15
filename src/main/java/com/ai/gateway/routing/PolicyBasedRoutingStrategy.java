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
import com.ai.gateway.routing.scoring.CandidateScoringContext;
import com.ai.gateway.routing.scoring.ScoredCandidate;
import com.ai.gateway.routing.scoring.CandidateScoreComponent;
import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.CandidateScoringEngine;
import com.ai.gateway.routing.selection.CandidateSelectionEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(3)
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

    private final CandidateScoringEngine
            candidateScoringEngine;

    private final CandidateSelectionEngine
            candidateSelectionEngine;

    /**
     * Spring production constructor. The complete routing pipeline is:
     * resolution -> eligibility -> hard constraints -> scoring -> selection.
     */
    @Autowired
    public PolicyBasedRoutingStrategy(
            RoutingPolicyService routingPolicyService,
            ProviderModelRegistryService providerModelRegistryService,
            CandidateProviderResolver candidateProviderResolver,
            CandidateModelResolver candidateModelResolver,
            CandidateEligibilityFilter candidateEligibilityFilter,
            CandidateConstraintEvaluator candidateConstraintEvaluator,
            CandidateScoringEngine candidateScoringEngine,
            CandidateSelectionEngine candidateSelectionEngine) {

        this.routingPolicyService = routingPolicyService;
        this.providerModelRegistryService = providerModelRegistryService;
        this.candidateProviderResolver = candidateProviderResolver;
        this.candidateModelResolver = candidateModelResolver;
        this.candidateEligibilityFilter = candidateEligibilityFilter;
        this.candidateConstraintEvaluator = candidateConstraintEvaluator;
        this.candidateScoringEngine = candidateScoringEngine;
        this.candidateSelectionEngine = candidateSelectionEngine;
    }

    /**
     * Backward-compatible constructor for existing isolated routing tests.
     *
     * <p>It preserves the pre-scoring test contract while production Spring
     * wiring always uses the full constructor above.</p>
     */
    public PolicyBasedRoutingStrategy(
            RoutingPolicyService routingPolicyService,
            ProviderModelRegistryService providerModelRegistryService,
            CandidateProviderResolver candidateProviderResolver,
            CandidateModelResolver candidateModelResolver,
            CandidateEligibilityFilter candidateEligibilityFilter,
            CandidateConstraintEvaluator candidateConstraintEvaluator) {

        this(
                routingPolicyService,
                providerModelRegistryService,
                candidateProviderResolver,
                candidateModelResolver,
                candidateEligibilityFilter,
                candidateConstraintEvaluator,
                new CompatibilityScoringEngine(),
                new com.ai.gateway.routing.selection.impl.CandidateSelectionEngineImpl());
    }

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
         * 6.5.5 Candidate Scoring
         *
         * Hard-constraint-eligible candidates are now scored as soft
         * preferences. Scoring never re-admits a candidate rejected by
         * 6.5.4.
         */
        CandidateScoringContext scoringContext =
                CandidateScoringContext.standard(policy);

        List<ScoredCandidate> scoredCandidates =
                candidateScoringEngine.score(
                        constraintEligibleCandidates,
                        scoringContext);

        if (scoredCandidates == null
                || scoredCandidates.isEmpty()) {

            throw new BusinessException(
                    "No routing candidate could be scored.");
        }

        /*
         * 6.5.6 Candidate Selection
         *
         * Selection operates only on scored candidates and is deterministic.
         */
        ScoredCandidate selectedScoredCandidate =
                candidateSelectionEngine.select(
                        scoredCandidates);

        RoutingCandidate selected =
                selectedScoredCandidate.candidate();

        /*
         * 6.5.7 Decision Metadata
         *
         * Keep the public routing decision compatible while exposing the
         * ranked decision context for observability and future analytics.
         */
        List<ScoredCandidate> rankedCandidates =
                candidateSelectionEngine instanceof
                        com.ai.gateway.routing.selection.impl.CandidateSelectionEngineImpl
                        impl
                        ? impl.rank(scoredCandidates)
                        : scoredCandidates;

        int selectedRank = 1;
        for (int index = 0; index < rankedCandidates.size(); index++) {
            if (rankedCandidates.get(index).candidate().equals(selected)) {
                selectedRank = index + 1;
                break;
            }
        }

        List<RoutingDecisionMetadata.RoutingCandidateMetadata> rankedMetadata =
                new ArrayList<>();

        for (int index = 0; index < rankedCandidates.size(); index++) {
            ScoredCandidate candidate =
                    rankedCandidates.get(index);

            rankedMetadata.add(
                    new RoutingDecisionMetadata.RoutingCandidateMetadata(
                            candidate.candidate().provider().name(),
                            candidate.candidate().model(),
                            candidate.totalScore(),
                            index + 1));
        }

        RoutingDecisionMetadata metadata =
                new RoutingDecisionMetadata(
                        selectedScoredCandidate.totalScore(),
                        selectedRank,
                        rankedCandidates.size(),
                        selectedRank == 1
                                ? "HIGHEST_SCORE"
                                : "DETERMINISTIC_RANKING",
                        scoringContext.extensiveResearchEnabled(),
                        scoringContext.executionRole(),
                        rankedMetadata);

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
                RoutingStrategy.POLICY_BASED,
                metadata);
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


    
    /**
     * Compatibility-only scorer used by the legacy six-argument constructor.
     * Spring never uses this path.
     */
    private static final class CompatibilityScoringEngine
            implements CandidateScoringEngine {

        @Override
        public List<ScoredCandidate> score(
                List<RoutingCandidate> candidates,
                CandidateScoringContext context) {

            List<ScoredCandidate> result = new ArrayList<>();

            for (RoutingCandidate candidate : candidates) {
                boolean preferred =
                        context.policy().preferredProvider() == candidate.provider()
                                && context.policy().preferredModel() != null
                                && context.policy().preferredModel().equals(candidate.model());

                double score = preferred ? 1.0 : 0.0;

                CandidateScoreComponent component =
                        new CandidateScoreComponent(
                                CandidateScoreDimension.POLICY_PREFERENCE,
                                score,
                                score,
                                1.0,
                                score);

                result.add(
                        new ScoredCandidate(
                                candidate,
                                List.of(component),
                                score));
            }

            return List.copyOf(result);
        }
    }
}