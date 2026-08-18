package com.ai.gateway.cost.service;

import com.ai.gateway.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.cost.dto.PreRequestCostRequest;

public interface PreRequestCostEstimator {

    PreRequestCostEstimate estimate(PreRequestCostRequest request);
}
