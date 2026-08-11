package com.ai.gateway.budget;

import com.ai.gateway.exception.BusinessException;

public class BudgetExceededException
        extends BusinessException {

    public BudgetExceededException(
            String message) {

        super(message);
    }
}
