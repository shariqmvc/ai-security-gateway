package com.ai.gateway.rag.search;

import java.util.List;

public interface RagQueryTransformer {
    List<String> transform(String query);
}
