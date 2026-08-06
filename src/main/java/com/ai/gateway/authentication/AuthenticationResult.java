package com.ai.gateway.authentication;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResult {

    private boolean authenticated;

    private AuthenticationContext context;

    private String message;

}
