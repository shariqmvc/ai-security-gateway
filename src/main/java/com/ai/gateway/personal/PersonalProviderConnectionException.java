package com.ai.gateway.personal;

import com.ai.gateway.exception.BusinessException;

public class PersonalProviderConnectionException extends BusinessException {

    public PersonalProviderConnectionException(String message) {
        super(message);
    }
}
