package com.ai.gateway.routing.constraint;

import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.policy.RoutingPolicy;
import com.ai.gateway.routing.registry.ModelDefinition;
import com.ai.gateway.routing.registry.ModelRegistry;
import com.ai.gateway.routing.registry.ProviderDefinition;
import com.ai.gateway.routing.registry.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Default deterministic hard-constraint evaluator for routing candidates.
 *
 * <p>The evaluator deliberately does not score candidates and does not
 * select a winner. A candidate either satisfies all mandatory constraints
 * or is removed before scoring.</p>
 *
 * <p>At the current registry maturity, provider/model availability is
 * represented by their ENABLED registry state. Live health/capacity
 * availability is intentionally not inferred here and can be added later
 * as a separate runtime constraint.</p>
 */
@Service
@RequiredArgsConstructor
public class CandidateConstraintEvaluatorImpl
        implements CandidateConstraintEvaluator {

    private final ProviderRegistry providerRegistry;
    private final ModelRegistry modelRegistry;

    @Override
    public CandidateConstraintEvaluation evaluate(
            RoutingCandidate candidate,
            RoutingPolicy policy) {

        if (candidate == null) {
            throw new IllegalArgumentException(
                    "Candidate is required for constraint evaluation.");
        }

        List<ConstraintEvaluationResult> results = new ArrayList<>();

        results.add(
                ConstraintEvaluationResult.pass(
                        HardConstraintType.CANDIDATE_VALID,
                        "Candidate contains a provider and model."));

        boolean policyValid = policy != null && policy.enabled();

        if (!policyValid) {
            results.add(
                    ConstraintEvaluationResult.fail(
                            HardConstraintType.PROVIDER_ALLOWED_BY_POLICY,
                            "Routing policy is null or disabled."));
            results.add(
                    ConstraintEvaluationResult.fail(
                            HardConstraintType.MODEL_ALLOWED_BY_POLICY,
                            "Routing policy is null or disabled."));
        } else {
            boolean providerAllowed =
                    policy.allowsProvider(candidate.provider());

            results.add(providerAllowed
                    ? ConstraintEvaluationResult.pass(
                            HardConstraintType.PROVIDER_ALLOWED_BY_POLICY,
                            "Provider is allowed by routing policy.")
                    : ConstraintEvaluationResult.fail(
                            HardConstraintType.PROVIDER_ALLOWED_BY_POLICY,
                            "Provider is not allowed by routing policy."));

            boolean modelAllowed =
                    policy.allowsModel(candidate.model());

            results.add(modelAllowed
                    ? ConstraintEvaluationResult.pass(
                            HardConstraintType.MODEL_ALLOWED_BY_POLICY,
                            "Model is allowed by routing policy.")
                    : ConstraintEvaluationResult.fail(
                            HardConstraintType.MODEL_ALLOWED_BY_POLICY,
                            "Model is not allowed by routing policy."));
        }

        Optional<ProviderDefinition> providerDefinition =
                providerRegistry.find(candidate.provider());

        boolean providerEnabled = providerDefinition
                .map(ProviderDefinition::isEnabled)
                .orElse(false);

        results.add(providerEnabled
                ? ConstraintEvaluationResult.pass(
                        HardConstraintType.PROVIDER_ENABLED,
                        "Provider is enabled in the provider registry.")
                : ConstraintEvaluationResult.fail(
                        HardConstraintType.PROVIDER_ENABLED,
                        "Provider is not enabled or is not registered."));

        Optional<ModelDefinition> modelDefinition =
                modelRegistry.find(
                        candidate.provider(),
                        candidate.model());

        boolean modelRegistered = modelDefinition.isPresent();

        results.add(modelRegistered
                ? ConstraintEvaluationResult.pass(
                        HardConstraintType.MODEL_REGISTERED,
                        "Model is registered for the candidate provider.")
                : ConstraintEvaluationResult.fail(
                        HardConstraintType.MODEL_REGISTERED,
                        "Model is not registered for the candidate provider."));

        boolean modelEnabled = modelDefinition
                .map(ModelDefinition::isEnabled)
                .orElse(false);

        results.add(modelEnabled
                ? ConstraintEvaluationResult.pass(
                        HardConstraintType.MODEL_ENABLED,
                        "Model is enabled in the model registry.")
                : ConstraintEvaluationResult.fail(
                        HardConstraintType.MODEL_ENABLED,
                        "Model is not enabled or is not registered."));

        boolean eligible = results.stream()
                .allMatch(ConstraintEvaluationResult::passed);

        return new CandidateConstraintEvaluation(
                candidate,
                eligible,
                results);
    }
}
