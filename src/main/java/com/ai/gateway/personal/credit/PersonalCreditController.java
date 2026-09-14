package com.ai.gateway.personal.credit;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.credit.entity.PersonalCreditLedger;
import com.ai.gateway.personal.credit.entity.PersonalCreditWallet;
import com.ai.gateway.personal.credit.service.PersonalCreditService;
import com.ai.gateway.authentication.AuthenticationType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/personal/credits")
@RequiredArgsConstructor
public class PersonalCreditController {

    private final PersonalCreditService creditService;
    private final com.ai.gateway.personal.credit.repository.PersonalCreditLedgerRepository ledgerRepository;

    @GetMapping("/wallet")
    public PersonalCreditWallet wallet(HttpServletRequest request) {
        return creditService.getOrCreateWallet(context(request).getPersonalAccountId());
    }

    @GetMapping("/ledger")
    public List<PersonalCreditLedger> ledger(HttpServletRequest request) {
        return ledgerRepository.findByPersonalAccountIdOrderByCreatedAtDesc(
                context(request).getPersonalAccountId());
    }

    private AuthenticationContext context(HttpServletRequest request) {
        AuthenticationContext context =
                (AuthenticationContext) request.getAttribute(
                        AuthenticationConstants.AUTH_CONTEXT);
        if (context == null || !context.isPersonalPrincipal()
                || context.getPersonalAccountId() == null
                || context.getAuthenticationType() != AuthenticationType.PERSONAL_SESSION) {
            throw new AccessDeniedException(
                    "Personal session authentication is required.");
        }
        return context;
    }
}
