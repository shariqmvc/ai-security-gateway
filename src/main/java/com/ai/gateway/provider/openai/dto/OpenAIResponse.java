package com.ai.gateway.provider.openai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAIResponse {
    private String id;

    private List<Output> output;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Output {

        private List<Content> content;

    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {

        private String text;



    }

    private Usage usage;
}
