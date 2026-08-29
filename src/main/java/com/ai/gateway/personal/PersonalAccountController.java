package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.dto.PersonalUserResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/personal")
@RequiredArgsConstructor
public class PersonalAccountController {

    private final PersonalAuthService personalAuthService;

    @GetMapping("/me")
    public PersonalUserResponse me(HttpServletRequest request) {
        AuthenticationContext context =
                (AuthenticationContext) request.getAttribute(
                        AuthenticationConstants.AUTH_CONTEXT);

        return personalAuthService.me(context);
    }
}
