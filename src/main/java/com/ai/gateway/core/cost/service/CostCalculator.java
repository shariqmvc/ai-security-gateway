package com.ai.gateway.core.cost.service;

import com.ai.gateway.core.cost.dto.CostRequest;
import com.ai.gateway.core.cost.dto.CostResponse;

public interface CostCalculator {

    CostResponse calculate(CostRequest request);


}
