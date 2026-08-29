package com.ai.gateway.core.cost.service;

import com.ai.gateway.core.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.core.cost.dto.PreRequestCostRequest;

public interface PreRequestCostEstimator {

    PreRequestCostEstimate estimate(PreRequestCostRequest request);
}
