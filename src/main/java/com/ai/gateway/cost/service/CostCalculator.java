package com.ai.gateway.cost.service;

import com.ai.gateway.cost.dto.CostRequest;
import com.ai.gateway.cost.dto.CostResponse;

public interface CostCalculator {

    CostResponse calculate(CostRequest request);


}
