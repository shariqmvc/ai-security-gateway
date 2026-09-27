package com.ai.gateway.core.provider.claude;

import com.ai.gateway.config.ProviderHttpProperties;
import com.ai.gateway.config.ProviderHttpRequestFactory;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.contract.Usage;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.multimodal.MediaContent;
import com.ai.gateway.core.multimodal.MediaSourceType;
import com.ai.gateway.core.multimodal.MediaTypeKind;
import com.ai.gateway.core.observability.PerformanceLogger;
import com.ai.gateway.core.provider.*;
import com.ai.gateway.personal.PersonalProviderCredentialResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

@Service
public class ClaudeProvider implements AIProvider, StreamingAIProvider {
    @Value("{anthropic.base-url:https://api.anthropic.com}") private String baseUrl;
    @Value("{anthropic.api-key:}") private String apiKey;
    @Value("{anthropic.model:claude-sonnet-4-6}") private String model;
    @Value("{anthropic.max-tokens:4096}") private int maxTokens;

    private final ObjectMapper mapper;
    private final PerformanceLogger logger;
    private final PersonalProviderCredentialResolver credentials;
    private final RestTemplate rest=new RestTemplate();

    public ClaudeProvider(ObjectMapper mapper,PerformanceLogger logger,PersonalProviderCredentialResolver credentials,ProviderHttpProperties httpProperties){
        this.mapper=mapper;this.logger=logger;this.credentials=credentials;
        this.rest.setRequestFactory(new ProviderHttpRequestFactory(Provider.CLAUDE,httpProperties.forProvider(Provider.CLAUDE)));
    }
    @Override public Provider provider(){return Provider.CLAUDE;}
    @Override public String defaultModel(){return model;}

    @Override public AIResponse chat(AIRequest request){
        UUID id=id();long started=System.nanoTime();String selected=selectedModel(request);
        logger.providerStart(id,provider().name(),selected,attempt());
        try{
            JsonNode root=rest.postForObject(endpoint(),new HttpEntity<>(body(request,false),headers(request)),JsonNode.class);
            String answer=root.at("/content/0/text").asText("");
            if(answer.isBlank())throw new IllegalStateException("Anthropic returned an empty response.");
            JsonNode usage=root.path("usage");int input=usage.path("input_tokens").asInt(0),output=usage.path("output_tokens").asInt(0);
            logger.providerCompleted(id,provider().name(),selected,attempt(),elapsed(started),"HTTP_200");
            return AIResponse.builder().response(answer).provider(provider()).model(selected)
                    .usage(Usage.builder().inputTokens(input).outputTokens(output).totalTokens(input+output).latencyMs(elapsed(started)).build()).build();
        }catch(RuntimeException ex){logger.providerCompleted(id,provider().name(),selected,attempt(),elapsed(started),"FAILED:"+ex.getClass().getSimpleName());throw ex;}
    }

    @Override public AIStreamResult stream(AIRequest request,Consumer<String> consumer){
        UUID id=id();long started=System.nanoTime();String selected=selectedModel(request);
        logger.providerStart(id,provider().name(),selected,attempt());
        StringBuilder full=new StringBuilder();int[] input={0},output={0};
        try{
            rest.execute(endpoint(),HttpMethod.POST,req->{req.getHeaders().putAll(headers(request));req.getBody().write(mapper.writeValueAsBytes(body(request,true)));},response->{
                try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(response.getBody(),StandardCharsets.UTF_8))){
                    String line;
                    while((line=reader.readLine())!=null){
                        if(!line.startsWith("data:"))continue;
                        String raw=line.substring(5).trim();if(raw.isEmpty())continue;
                        JsonNode root=mapper.readTree(raw);String type=root.path("type").asText();
                        if("content_block_delta".equals(type)){
                            String piece=root.at("/delta/text").asText("");
                            if(!piece.isBlank()){full.append(piece);consumer.accept(piece);}
                        }else if("message_start".equals(type)){input[0]=root.at("/message/usage/input_tokens").asInt(0);}
                        else if("message_delta".equals(type)){output[0]=root.at("/usage/output_tokens").asInt(output[0]);}
                    }
                }return null;
            });
        }catch(Exception ex){logger.providerCompleted(id,provider().name(),selected,attempt(),elapsed(started),"FAILED:"+ex.getClass().getSimpleName());throw new RuntimeException("Streaming Anthropic request failed.",ex);}
        if(full.isEmpty())throw new IllegalStateException("Anthropic returned an empty streaming response.");
        long latency=elapsed(started);logger.providerCompleted(id,provider().name(),selected,attempt(),latency,"HTTP_200");
        return AIStreamResult.builder().response(full.toString()).provider(provider()).model(selected).inputTokens(input[0]).outputTokens(output[0]).totalTokens(input[0]+output[0]).latencyMs(latency).build();
    }

    private HttpHeaders headers(AIRequest request){HttpHeaders h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.setAccept(List.of(MediaType.APPLICATION_JSON,MediaType.TEXT_EVENT_STREAM));h.set("x-api-key",key(request));h.set("anthropic-version","2023-06-01");return h;}
    private String key(AIRequest request){if("BYOK".equalsIgnoreCase(request.getBillingMode())&&request.getPersonalAccountId()!=null)return credentials.resolveApiKey(request.getPersonalAccountId(),provider());if(apiKey==null||apiKey.isBlank())throw new IllegalStateException("No API key configured for ANTHROPIC.");return apiKey;}
    private Map<String,Object> body(AIRequest request,boolean stream){
        List<Object> content=new ArrayList<>();
        if(request.getPrompt()!=null&&!request.getPrompt().isBlank())content.add(Map.of("type","text","text",request.getPrompt()));
        if(request.getMedia()!=null)for(MediaContent media:request.getMedia()){
            if(media.getType()!=MediaTypeKind.IMAGE)continue;
            Map<String,Object> source=media.getSourceType()==MediaSourceType.URL?Map.of("type","url","url",media.getUrl()):Map.of("type","base64","media_type",media.getMimeType(),"data",media.getData());
            content.add(Map.of("type","image","source",source));
        }
        Map<String,Object> body=new LinkedHashMap<>();body.put("model",selectedModel(request));body.put("max_tokens",maxTokens);body.put("messages",List.of(Map.of("role","user","content",content)));body.put("stream",stream);return body;
    }
    private String endpoint(){return baseUrl.replaceAll("/$","")+"/v1/messages";}
    private String selectedModel(AIRequest r){return r.getModel()==null||r.getModel().isBlank()?model:r.getModel();}
    private int attempt(){try{return Math.max(1,Integer.parseInt(Optional.ofNullable(org.slf4j.MDC.get("providerAttempt")).orElse("1")));}catch(Exception e){return 1;}}
    private UUID id(){try{return UUID.fromString(Optional.ofNullable(org.slf4j.MDC.get("requestId")).orElse(""));}catch(Exception e){return null;}}
    private long elapsed(long s){return(System.nanoTime()-s)/1_000_000L;}
}