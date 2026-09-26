package com.ai.gateway.personal.chat;

import com.ai.gateway.authentication.*;
import com.ai.gateway.personal.chat.entity.*;
import com.ai.gateway.personal.chat.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/personal/chat/sessions")
@RequiredArgsConstructor
public class PersonalChatController {
    private final PersonalChatSessionRepository sessionRepository;
    private final PersonalChatMessageRepository messageRepository;

    @GetMapping
    public List<SessionResponse> list(HttpServletRequest request) {
        UUID accountId=context(request).getPersonalAccountId();
        return sessionRepository.findByPersonalAccountIdOrderByUpdatedAtDesc(accountId)
            .stream().map(this::session).toList();
    }

    @PostMapping
    public SessionResponse create(HttpServletRequest request,@RequestBody CreateSessionRequest body) {
        UUID accountId=context(request).getPersonalAccountId();
        String title=normalizeTitle(body==null?null:body.title());
        PersonalChatSession saved=sessionRepository.save(PersonalChatSession.builder()
            .personalAccountId(accountId).title(title).build());
        return session(saved);
    }

    @PostMapping("/{sessionId}/branch")
    @Transactional
    public BranchResponse branch(HttpServletRequest request,@PathVariable UUID sessionId,@RequestBody BranchRequest body) {
        PersonalChatSession sourceSession=requireSession(request,sessionId);
        if(body==null||body.messageId()==null) throw new IllegalArgumentException("messageId is required.");
        PersonalChatMessage branchMessage=messageRepository.findById(body.messageId())
            .filter(m->sessionId.equals(m.getSessionId()))
            .orElseThrow(()->new IllegalArgumentException("Branch message not found in this session."));

        List<PersonalChatMessage> sourceMessages=messageRepository.findBySessionIdAndSequenceNoLessThanEqualOrderBySequenceNoAsc(
            sessionId,branchMessage.getSequenceNo());
        PersonalChatSession branch=sessionRepository.save(PersonalChatSession.builder()
            .personalAccountId(sourceSession.getPersonalAccountId())
            .title(normalizeTitle(body.title()==null||body.title().isBlank()?"Branch: "+sourceSession.getTitle():body.title()))
            .parentSessionId(sourceSession.getId())
            .branchMessageId(branchMessage.getId())
            .build());

        List<PersonalChatMessage> copies=new ArrayList<>(sourceMessages.size());
        int sequence=0;
        for(PersonalChatMessage source:sourceMessages){
            copies.add(PersonalChatMessage.builder()
                .sessionId(branch.getId())
                .role(source.getRole())
                .content(source.getContent())
                .requestId(source.getRequestId())
                .attachmentsJson(source.getAttachmentsJson())
                .sequenceNo(sequence++)
                .createdAt(source.getCreatedAt())
                .build());
        }
        List<PersonalChatMessage> savedMessages=messageRepository.saveAllAndFlush(copies);
        return new BranchResponse(session(branch),savedMessages.stream().map(this::message).toList());
    }

    @GetMapping("/{sessionId}/messages")
    public List<MessageResponse> messages(HttpServletRequest request,@PathVariable UUID sessionId) {
        requireSession(request,sessionId);
        return messageRepository.findBySessionIdOrderBySequenceNoAsc(sessionId)
            .stream().map(this::message).toList();
    }

    @PutMapping("/{sessionId}")
    public SessionResponse rename(HttpServletRequest request,@PathVariable UUID sessionId,@RequestBody CreateSessionRequest body) {
        PersonalChatSession session=requireSession(request,sessionId);
        session.setTitle(normalizeTitle(body==null?null:body.title()));
        return session(sessionRepository.save(session));
    }

    @PutMapping("/{sessionId}/messages")
    @Transactional
    public List<MessageResponse> replaceMessages(HttpServletRequest request,@PathVariable UUID sessionId,@RequestBody List<SaveMessageRequest> body) {
        PersonalChatSession session=requireSession(request,sessionId);
        messageRepository.deleteBySessionId(sessionId);
        messageRepository.flush();

        List<SaveMessageRequest> input=body==null?List.of():body;
        List<PersonalChatMessage> saved=new ArrayList<>();
        int sequence=0;
        for(SaveMessageRequest item:input) {
            if(item==null || item.content()==null || item.content().isBlank()) continue;
            String role=item.role()==null?"user":item.role().trim().toLowerCase(Locale.ROOT);
            if(!Set.of("user","assistant","system").contains(role)) {
                throw new IllegalArgumentException("Unsupported chat message role: "+role);
            }
            saved.add(PersonalChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(item.content())
                .requestId(item.requestId())
                .sequenceNo(sequence++)
                .createdAt(item.createdAt()==null?LocalDateTime.now():item.createdAt())
                .build());
        }
        messageRepository.saveAllAndFlush(saved);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        return saved.stream().map(this::message).toList();
    }

    @DeleteMapping("/{sessionId}")
    @Transactional
    public void delete(HttpServletRequest request,@PathVariable UUID sessionId) {
        PersonalChatSession session=requireSession(request,sessionId);
        messageRepository.deleteBySessionId(session.getId());
        sessionRepository.delete(session);
    }

    private PersonalChatSession requireSession(HttpServletRequest request,UUID sessionId) {
        UUID accountId=context(request).getPersonalAccountId();
        return sessionRepository.findByIdAndPersonalAccountId(sessionId,accountId)
            .orElseThrow(()->new AccessDeniedException("Chat session not found."));
    }

    private AuthenticationContext context(HttpServletRequest request) {
        AuthenticationContext c=(AuthenticationContext)request.getAttribute(AuthenticationConstants.AUTH_CONTEXT);
        if(c==null||!c.isPersonalPrincipal()||c.getPersonalAccountId()==null
            ||c.getAuthenticationType()!=AuthenticationType.PERSONAL_SESSION)
            throw new AccessDeniedException("Personal session authentication is required.");
        return c;
    }

    private SessionResponse session(PersonalChatSession s) {
        return new SessionResponse(s.getId(),s.getTitle(),s.getCreatedAt(),s.getUpdatedAt(),s.getParentSessionId(),s.getBranchMessageId());
    }

    private MessageResponse message(PersonalChatMessage m) {
        return new MessageResponse(m.getId(),m.getRole(),m.getContent(),m.getSequenceNo(),m.getCreatedAt(),m.getRequestId());
    }

    private String normalizeTitle(String title) {
        String value=title==null?"New chat":title.trim().replaceAll("\\s+"," ");
        if(value.isBlank()) return "New chat";
        return value.length()>255?value.substring(0,255):value;
    }

    public record CreateSessionRequest(String title) {}
    public record BranchRequest(UUID messageId,String title) {}
    public record SaveMessageRequest(String role,String content,LocalDateTime createdAt,UUID requestId) {}
    public record SessionResponse(UUID id,String title,LocalDateTime createdAt,LocalDateTime updatedAt,UUID parentSessionId,UUID branchMessageId) {}
    public record MessageResponse(UUID id,String role,String content,Integer sequenceNo,LocalDateTime createdAt,UUID requestId) {}
    public record BranchResponse(SessionResponse session,List<MessageResponse> messages) {}
}
