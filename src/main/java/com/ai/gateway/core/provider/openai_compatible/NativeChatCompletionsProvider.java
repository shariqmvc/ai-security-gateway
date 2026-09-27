package com.ai.gateway.core.provider.openai_compatible;

import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.contract.Usage;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.multimodal.MediaContent;
import com.ai.gateway.core.multimodal.MediaSourceType;
import com.ai.gateway.core.multimodal.MediaTypeKind;
import com.ai.gateway.core.observability.PerformanceLogger;
import com.ai.gateway.core.provider.AIProvider;
import com.ai.gateway.core.provider.AIStreamResult;
import com.ai.gateway.core.provider.StreamingAIProvider;
import com.ai.gateway.personal.PersonalProviderCredentialResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public abstract class NativeChatCompletionsProvider implements AIProvider, StreamingAIProvider {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PerformanceLogger performanceLogger;
    private final PersonalProviderCredentialResolver credentialResolver;

    protected NativeChatCompletionsProvider(RestTemplate restTemplate, ObjectMapper objectMapper,
            PerformanceLogger performanceLogger, PersonalProviderCredentialResolver credentialResolver) {
        this.restTemplate=restTemplate; this.objectMapper=objectMapper;
        this.performanceLogger=performanceLogger; this.credentialResolver=credentialResolver;
    }

    protected abstract String baseUrl();
    protected abstract String configuredApiKey();
    protected abstract String configuredModel();

    @Override public String defaultModel(){ return configuredModel(); }

    @Override public AIResponse chat(AIRequest request) {
        UUID id=id(); long started=System.nanoTime(); String selected=selectedModel(request);
        performanceLogger.providerStart(id,provider().name(),selected,attempt());
        try {
            Map<String,Object> body=new LinkedHashMap<>();
            body.put("model",selected); body.put("messages",List.of(buildMessage(request))); body.put("stream",false);
            JsonNode root=restTemplate.postForObject(endpoint(),new HttpEntity<>(body,headers(request)),JsonNode.class);
            String answer=text(root.at("/choices/0/message/content"));
            if(answer.isBlank()) throw new IllegalStateException(provider()+" returned an empty response.");
            JsonNode usage=root.path("usage");
            int input=usage.path("prompt_tokens").asInt(0), output=usage.path("completion_tokens").asInt(0);
            performanceLogger.providerCompleted(id,provider().name(),selected,attempt(),elapsed(started),"HTTP_200");
            return AIResponse.builder().response(answer).provider(provider()).model(selected)
                    .usage(Usage.builder().inputTokens(input).outputTokens(output).totalTokens(usage.path("total_tokens").asInt(input+output))
                            .latencyMs(elapsed(started)).build()).build();
        } catch(RuntimeException ex) {
            performanceLogger.providerCompleted(id,provider().name(),selected,attempt(),elapsed(started),"FAILED:"+ex.getClass().getSimpleName());
            throw ex;
        }
    }

    @Override public AIStreamResult stream(AIRequest request, Consumer<String> consumer) {
        UUID id=id(); long started=System.nanoTime(); String selected=selectedModel(request);
        performanceLogger.providerStart(id,provider().name(),selected,attempt());
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("model",selected); body.put("messages",List.of(buildMessage(request))); body.put("stream",true);
        body.put("stream_options",Map.of("include_usage",true));
        StringBuilder full=new StringBuilder(); int[] input={0},output={0},total={0};
        try {
            restTemplate.execute(endpoint(),HttpMethod.POST,req->{
                req.getHeaders().putAll(headers(request));
                req.getBody().write(objectMapper.writeValueAsBytes(body));
            },response->{
                try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(response.getBody(),StandardCharsets.UTF_8))){
                    String line;
                    while((line=reader.readLine())!=null){
                        if(!line.startsWith("data:")) continue;
                        String data=line.substring(5).trim();
                        if(data.isEmpty()||"[DONE]".equals(data)) continue;
                        JsonNode root=objectMapper.readTree(data);
                        String piece=text(root.at("/choices/0/delta/content"));
                        if(!piece.isBlank()){full.append(piece);consumer.accept(piece);}
                        JsonNode usage=root.path("usage");
                        if(usage.isObject()){input[0]=usage.path("prompt_tokens").asInt(input[0]);output[0]=usage.path("completion_tokens").asInt(output[0]);total[0]=usage.path("total_tokens").asInt(input[0]+output[0]);}
                    }
                }
                return null;
            });
        } catch(Exception ex) {
            performanceLogger.providerCompleted(id,provider().name(),selected,attempt(),elapsed(started),"FAILED:"+ex.getClass().getSimpleName());
            throw new RuntimeException("Streaming "+provider()+" request failed.",ex);
        }
        if(full.isEmpty()) throw new IllegalStateException(provider()+" returned an empty streaming response.");
        long latency=elapsed(started);
        performanceLogger.providerCompleted(id,provider().name(),selected,attempt(),latency,"HTTP_200");
        return AIStreamResult.builder().response(full.toString()).provider(provider()).model(selected)
                .inputTokens(input[0]).outputTokens(output[0]).totalTokens(total[0]).latencyMs(latency).build();
    }

    private HttpHeaders headers(AIRequest request){
        HttpHeaders h=new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON,MediaType.TEXT_EVENT_STREAM)); h.setBearerAuth(apiKey(request)); return h;
    }
    private String apiKey(AIRequest request){
        if("BYOK".equalsIgnoreCase(request.getBillingMode())&&request.getPersonalAccountId()!=null)
            return credentialResolver.resolveApiKey(request.getPersonalAccountId(),provider());
        String key=configuredApiKey();
        if(key==null||key.isBlank()) throw new IllegalStateException("No API key configured for "+provider()+".");
        return key;
    }
    private Map<String,Object> buildMessage(AIRequest request){
        List<Map<String,Object>> content=new ArrayList<>();
        if(request.getPrompt()!=null&&!request.getPrompt().isBlank()) content.add(Map.of("type","text","text",request.getPrompt()));
        if(request.getMedia()!=null) for(MediaContent media:request.getMedia()){
            if(media.getType()!=MediaTypeKind.IMAGE) continue;
            String url=media.getSourceType()==MediaSourceType.URL?media.getUrl():"data:"+media.getMimeType()+";base64,"+media.getData();
            content.add(Map.of("type","image_url","image_url",Map.of("url",url,"detail",media.getDetail()==null?"auto":media.getDetail())));
        }
        return Map.of("role","user","content",content);
    }
    private String endpoint(){return baseUrl().replaceAll("/$","")+"/chat/completions";}
    private String selectedModel(AIRequest r){return r.getModel()==null||r.getModel().isBlank()?configuredModel():r.getModel();}
    private String text(JsonNode n){
        if(n==null||n.isMissingNode()||n.isNull()) return "";
        if(n.isTextual()) return n.asText();
        if(n.isArray()){StringBuilder s=new StringBuilder();n.forEach(x->{if(x.has("text"))s.append(x.path("text").asText());});return s.toString();}
        return "";
    }
    private int attempt(){try{return Math.max(1,Integer.parseInt(Optional.ofNullable(org.slf4j.MDC.get("providerAttempt")).orElse("1")));}catch(Exception e){return 1;}}
    private UUID id(){try{return UUID.fromString(Optional.ofNullable(org.slf4j.MDC.get("requestId")).orElse(""));}catch(Exception e){return null;}}
    private long elapsed(long s){return(System.nanoTime()-s)/1_000_000L;}
}
