package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.dto.PersonalProviderConnectRequest;
import com.ai.gateway.personal.dto.PersonalProviderConnectionResponse;
import com.ai.gateway.personal.dto.PersonalProviderValidationResponse;

import java.util.List;

public interface PersonalProviderConnectionService {

    List<PersonalProviderConnectionResponse> list(AuthenticationContext context);

    PersonalProviderConnectionResponse connect(
            AuthenticationContext context,
            PersonalProviderConnectRequest request);

    PersonalProviderValidationResponse validate(
            AuthenticationContext context,
            Provider provider);

    void disconnect(AuthenticationContext context, Provider provider);
}
