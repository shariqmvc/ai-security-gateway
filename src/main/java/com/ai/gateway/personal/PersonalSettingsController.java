package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.dto.PersonalEffectiveSettingsResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/personal/settings")
@RequiredArgsConstructor
public class PersonalSettingsController {

    private final PersonalSettingsService settingsService;

    @GetMapping
    public PersonalEffectiveSettingsResponse get(HttpServletRequest request) {
        AuthenticationContext context =
                (AuthenticationContext) request.getAttribute(
                        AuthenticationConstants.AUTH_CONTEXT);

        return settingsService.getEffectiveSettings(context);
    }
}
