package com.ai.gateway.personal.rag;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.personal.PersonalFeatureEntitlementService;
import com.ai.gateway.rag.document.DocumentStatus;
import com.ai.gateway.rag.document.dto.DocumentRegistrationRequest;
import com.ai.gateway.rag.document.dto.DocumentResponse;
import com.ai.gateway.rag.embedding.EmbeddingProvider;
import com.ai.gateway.rag.embedding.EmbeddingProviderFactory;
import com.ai.gateway.rag.embedding.EmbeddingVector;
import com.ai.gateway.rag.embedding.EmbeddingVectorFormatter;
import com.ai.gateway.rag.ingestion.DocumentChunk;
import com.ai.gateway.rag.ingestion.DocumentChunker;
import com.ai.gateway.rag.ingestion.DocumentChunkerFactory;
import com.ai.gateway.rag.ingestion.DocumentParser;
import com.ai.gateway.rag.ingestion.ParsedDocument;
import com.ai.gateway.rag.ingestion.RagIngestionProperties;
import com.ai.gateway.rag.ingestion.TextNormalizer;
import com.ai.gateway.rag.knowledge.ChunkingStrategy;
import com.ai.gateway.rag.knowledge.KnowledgeBaseStatus;
import com.ai.gateway.rag.knowledge.dto.KnowledgeBaseCreateRequest;
import com.ai.gateway.rag.knowledge.dto.KnowledgeBaseResponse;
import com.ai.gateway.rag.search.dto.RagSearchRequest;
import com.ai.gateway.rag.search.dto.RagSearchResponse;
import com.ai.gateway.rag.search.dto.RagSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalRagService {
    private final JdbcTemplate jdbc;
    private final PersonalFeatureEntitlementService entitlementService;
    private final EmbeddingProviderFactory providerFactory;
    private final com.ai.gateway.rag.embedding.RagEmbeddingProperties embeddingProperties;
    private final DocumentParser documentParser;
    private final TextNormalizer textNormalizer;
    private final DocumentChunkerFactory chunkerFactory;
    private final RagIngestionProperties ingestionProperties;

    public void authorize(AuthenticationContext context) {
        entitlementService.validate(context, Feature.RAG);
    }

    @Transactional
    public KnowledgeBaseResponse create(AuthenticationContext context, KnowledgeBaseCreateRequest req) {
        authorize(context); UUID account = account(context);
        if (req == null || req.getName() == null || req.getName().isBlank()) throw new BusinessException("Knowledge base name is required.");
        String name = req.getName().trim();
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM PERSONAL_KNOWLEDGE_BASES WHERE personal_account_id=? AND lower(name)=lower(?)", Integer.class, account, name);
        if (count != null && count > 0) throw new BusinessException("Knowledge base already exists: " + name);
        UUID id = UUID.randomUUID(); LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO PERSONAL_KNOWLEDGE_BASES (id,personal_account_id,name,description,status,embedding_provider,embedding_model,vector_store,chunking_strategy,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, account, name, req.getDescription(), KnowledgeBaseStatus.ACTIVE.name(), trim(req.getEmbeddingProvider()), trim(req.getEmbeddingModel()),
                blank(req.getVectorStore()) ? "PGVECTOR" : req.getVectorStore().trim(),
                (req.getChunkingStrategy() == null ? ChunkingStrategy.TOKEN_AWARE : req.getChunkingStrategy()).name(), now, now);
        return get(context, id);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseResponse> list(AuthenticationContext context) {
        authorize(context); UUID account = account(context);
        return jdbc.query("SELECT * FROM PERSONAL_KNOWLEDGE_BASES WHERE personal_account_id=? ORDER BY created_at DESC", (rs,n) -> mapKb(rs), account);
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseResponse get(AuthenticationContext context, UUID id) {
        authorize(context); UUID account = account(context);
        return jdbc.query("SELECT * FROM PERSONAL_KNOWLEDGE_BASES WHERE id=? AND personal_account_id=?", (rs,n) -> mapKb(rs), id, account)
                .stream().findFirst().orElseThrow(() -> new BusinessException("Knowledge base not found: " + id));
    }

    @Transactional
    public void archive(AuthenticationContext context, UUID id) {
        authorize(context); UUID account = account(context);
        if (jdbc.update("UPDATE PERSONAL_KNOWLEDGE_BASES SET status=?,updated_at=? WHERE id=? AND personal_account_id=?", KnowledgeBaseStatus.ARCHIVED.name(), LocalDateTime.now(), id, account) == 0)
            throw new BusinessException("Knowledge base not found: " + id);
    }

    @Transactional
    public DocumentResponse register(AuthenticationContext context, UUID kbId, DocumentRegistrationRequest req) {
        authorize(context); UUID account = account(context); ensureKb(account, kbId);
        if (req == null || req.getFileName() == null || req.getFileName().isBlank()) throw new BusinessException("Document file name is required.");
        UUID id = UUID.randomUUID(); LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO PERSONAL_RAG_DOCUMENTS (id,personal_account_id,knowledge_base_id,file_name,content_type,file_size_bytes,checksum_sha256,status,content,chunk_count,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, account, kbId, req.getFileName().trim(), req.getContentType(), req.getFileSizeBytes(), trim(req.getChecksumSha256()),
                DocumentStatus.REGISTERED.name(), req.getContent(), 0, now, now);
        return getDocument(context, kbId, id);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(AuthenticationContext context, UUID kbId) {
        authorize(context); UUID account = account(context); ensureKb(account,kbId);
        return jdbc.query("SELECT * FROM PERSONAL_RAG_DOCUMENTS WHERE personal_account_id=? AND knowledge_base_id=? ORDER BY created_at DESC", (rs,n)->mapDoc(rs), account,kbId);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(AuthenticationContext context, UUID kbId, UUID docId) {
        authorize(context); UUID account = account(context); ensureKb(account,kbId);
        return jdbc.query("SELECT * FROM PERSONAL_RAG_DOCUMENTS WHERE id=? AND personal_account_id=? AND knowledge_base_id=?", (rs,n)->mapDoc(rs), docId,account,kbId)
                .stream().findFirst().orElseThrow(() -> new BusinessException("RAG document not found: " + docId));
    }

    @Transactional
    public DocumentResponse upload(AuthenticationContext context, UUID kbId, MultipartFile file) {
        authorize(context); UUID account=account(context); ensureKb(account,kbId);
        if(file==null||file.isEmpty()) throw new BusinessException("Document file is required.");
        String name=safeFileName(file.getOriginalFilename());
        if(!documentParser.supports(name,file.getContentType())) throw new BusinessException("Unsupported document type: "+name);
        if(file.getSize()>ingestionProperties.getMaxFileSizeBytes()) throw new BusinessException("Document exceeds maximum allowed size.");
        try {
            byte[] bytes=file.getBytes(); String checksum=sha256(bytes);
            Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM PERSONAL_RAG_DOCUMENTS WHERE personal_account_id=? AND knowledge_base_id=? AND checksum_sha256=?",Integer.class,account,kbId,checksum);
            if(count!=null&&count>0) throw new BusinessException("Document already exists: "+checksum);
            UUID id=UUID.randomUUID(); LocalDateTime now=LocalDateTime.now();
            jdbc.update("INSERT INTO PERSONAL_RAG_DOCUMENTS (id,personal_account_id,knowledge_base_id,file_name,content_type,file_size_bytes,checksum_sha256,status,chunk_count,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    id,account,kbId,name,file.getContentType(),(long)bytes.length,checksum,DocumentStatus.REGISTERED.name(),0,now,now);
            Files.createDirectories(ingestionProperties.tempDirectoryPath());
            Path tmp=Files.createTempFile(ingestionProperties.tempDirectoryPath(),"personal-rag-",".upload"); Files.write(tmp,bytes);
            processFileAsync(account,kbId,id,tmp,name,file.getContentType());
            return getDocument(context,kbId,id);
        } catch(IOException e){ throw new BusinessException("Unable to stage document: "+name); }
    }

    @Async("gatewayAsyncExecutor")
    public void processFileAsync(UUID account, UUID kbId, UUID docId, Path file, String name, String contentType) {
        try {
            ParsedDocument parsed=documentParser.parse(file,name,contentType);
            ingestAndEmbed(account,kbId,docId,textNormalizer.normalize(parsed.text()),parsed.detectedContentType());
        } catch(Exception e){ markFailed(account,docId,e.getMessage()); }
        finally { try { Files.deleteIfExists(file); } catch(Exception ignored){} }
    }

    @Async("gatewayAsyncExecutor")
    public void embedAsync(AuthenticationContext context, UUID kbId, UUID docId) {
        UUID account=account(context);
        try {
            Map<String,Object> doc=document(account,kbId,docId);
            String content=(String)doc.get("content");
            if(blank(content)) throw new BusinessException("Document content is required before embedding.");
            ingestAndEmbed(account,kbId,docId,textNormalizer.normalize(content), (String)doc.get("content_type"));
        } catch(Exception e){ markFailed(account,docId,e.getMessage()); }
    }

    @Transactional(readOnly = true)
    public RagSearchResponse search(AuthenticationContext context, UUID kbId, RagSearchRequest req) {
        authorize(context); UUID account=account(context); Map<String,Object> k=kb(account,kbId);
        if(!KnowledgeBaseStatus.ACTIVE.name().equals(k.get("status"))) throw new BusinessException("Cannot search an archived knowledge base: "+kbId);
        if(!"PGVECTOR".equalsIgnoreCase(String.valueOf(k.get("vector_store")))) throw new BusinessException("RAG retrieval requires vectorStore=PGVECTOR.");
        if(req==null||blank(req.getQuery())) throw new BusinessException("Search query is required.");
        String providerName=blank((String)k.get("embedding_provider"))?embeddingProperties.getDefaultProvider():((String)k.get("embedding_provider")).trim().toUpperCase();
        EmbeddingProvider provider=providerFactory.get(providerName); String model=blank((String)k.get("embedding_model"))?provider.defaultModel():((String)k.get("embedding_model")).trim();
        EmbeddingVector q=provider.embed(List.of(req.getQuery().trim()),model).getFirst(); String vector=EmbeddingVectorFormatter.toPgVector(q);
        int limit=Math.max(req.getCandidateLimit(),req.getTopK());
        List<RagSearchResult> results=jdbc.query("""
            SELECT c.id,c.document_id,d.file_name,c.chunk_index,c.record_id,c.section_id,c.chunk_id,c.content,c.metadata_json,
                   1-(c.embedding OPERATOR(public.<=>) query_vector) similarity
            FROM PERSONAL_RAG_DOCUMENT_CHUNKS c
            JOIN PERSONAL_RAG_DOCUMENTS d ON d.id=c.document_id
            CROSS JOIN (SELECT ?::public.vector AS query_vector) qv
            WHERE c.personal_account_id=? AND d.personal_account_id=? AND d.knowledge_base_id=? AND d.status='INDEXED'
              AND c.embedding IS NOT NULL AND c.embedding_provider=? AND c.embedding_model=? AND c.embedding_dimension=?
            ORDER BY c.embedding OPERATOR(public.<=>) query_vector ASC,c.document_id,c.chunk_index,c.id LIMIT ?""",
            (rs,n)->RagSearchResult.builder().id(rs.getObject("id",UUID.class)).documentId(rs.getObject("document_id",UUID.class)).fileName(rs.getString("file_name"))
                    .chunkIndex(rs.getInt("chunk_index")).recordId(rs.getString("record_id")).sectionId(rs.getString("section_id")).chunkId(rs.getString("chunk_id"))
                    .content(rs.getString("content")).metadataJson(rs.getString("metadata_json")).similarity(rs.getDouble("similarity")).build(),
            vector,account,account,kbId,providerName,model,q.dimension(),limit);
        results=results.stream().filter(r->r.getSimilarity()>=req.getMinScore()).limit(req.getTopK()).toList();
        return RagSearchResponse.builder().knowledgeBaseId(kbId).query(req.getQuery().trim()).retrievalStrategy("VECTOR").embeddingProvider(providerName).embeddingModel(model).queryEmbeddingDimension(q.dimension()).topK(req.getTopK()).results(results).build();
    }

    private void ingestAndEmbed(UUID account,UUID kbId,UUID docId,String text,String contentType){
        jdbc.update("UPDATE PERSONAL_RAG_DOCUMENTS SET status=?,content=?,content_type=?,updated_at=?,error_message=NULL WHERE id=? AND personal_account_id=?",DocumentStatus.PROCESSING.name(),text,contentType,LocalDateTime.now(),docId,account);
        Map<String,Object> k=kb(account,kbId); DocumentChunker chunker=chunkerFactory.resolve(ChunkingStrategy.valueOf((String)k.get("chunking_strategy")));
        List<DocumentChunk> chunks=chunker.chunk(text); jdbc.update("DELETE FROM PERSONAL_RAG_DOCUMENT_CHUNKS WHERE document_id=? AND personal_account_id=?",docId,account);
        for(DocumentChunk c:chunks) jdbc.update("INSERT INTO PERSONAL_RAG_DOCUMENT_CHUNKS (id,personal_account_id,document_id,chunk_index,record_id,section_id,chunk_id,content,token_count,metadata_json,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),account,docId,c.index(),c.recordId(),c.sectionId(),c.chunkId(),c.content(),c.tokenCount(),c.metadataJson(),LocalDateTime.now());
        jdbc.update("UPDATE PERSONAL_RAG_DOCUMENTS SET status=?,chunk_count=?,updated_at=? WHERE id=? AND personal_account_id=?",DocumentStatus.READY_FOR_EMBEDDING.name(),chunks.size(),LocalDateTime.now(),docId,account);
        if(embeddingProperties.isEnabled()) embedAccountDocument(account,kbId,docId);
    }

    private void embedAccountDocument(UUID account,UUID kbId,UUID docId){
        try {
            Map<String,Object> k=kb(account,kbId); String providerName=blank((String)k.get("embedding_provider"))?embeddingProperties.getDefaultProvider():((String)k.get("embedding_provider")).trim().toUpperCase();
            EmbeddingProvider provider=providerFactory.get(providerName); String model=blank((String)k.get("embedding_model"))?provider.defaultModel():((String)k.get("embedding_model")).trim();
            List<String> texts=jdbc.query("SELECT content FROM PERSONAL_RAG_DOCUMENT_CHUNKS WHERE document_id=? AND personal_account_id=? ORDER BY chunk_index",(rs,n)->rs.getString(1),docId,account);
            if(texts.isEmpty()) throw new BusinessException("Cannot embed a document with no chunks: "+docId);
            jdbc.update("UPDATE PERSONAL_RAG_DOCUMENTS SET status=?,updated_at=? WHERE id=? AND personal_account_id=?",DocumentStatus.EMBEDDING.name(),LocalDateTime.now(),docId,account);
            List<EmbeddingVector> vectors=provider.embed(texts,model); if(vectors.size()!=texts.size()) throw new BusinessException("Embedding count does not match chunk count.");
            for(int i=0;i<vectors.size();i++) jdbc.update("UPDATE PERSONAL_RAG_DOCUMENT_CHUNKS SET embedding=?::public.vector,embedding_provider=?,embedding_model=?,embedding_dimension=?,embedded_at=? WHERE document_id=? AND personal_account_id=? AND chunk_index=?",
                    EmbeddingVectorFormatter.toPgVector(vectors.get(i)),providerName,model,vectors.get(i).dimension(),LocalDateTime.now(),docId,account,i);
            jdbc.update("UPDATE PERSONAL_RAG_DOCUMENTS SET status=?,updated_at=?,error_message=NULL WHERE id=? AND personal_account_id=?",DocumentStatus.INDEXED.name(),LocalDateTime.now(),docId,account);
        } catch(Exception e){ markFailed(account,docId,e.getMessage()); }
    }

    private Map<String,Object> kb(UUID account,UUID id){try{return jdbc.queryForMap("SELECT * FROM PERSONAL_KNOWLEDGE_BASES WHERE id=? AND personal_account_id=?",id,account);}catch(Exception e){throw new BusinessException("Knowledge base not found: "+id);}}
    private Map<String,Object> document(UUID account,UUID kbId,UUID id){try{return jdbc.queryForMap("SELECT * FROM PERSONAL_RAG_DOCUMENTS WHERE id=? AND personal_account_id=? AND knowledge_base_id=?",id,account,kbId);}catch(Exception e){throw new BusinessException("RAG document not found: "+id);}}
    private void ensureKb(UUID account,UUID id){kb(account,id);}
    private UUID account(AuthenticationContext c){if(c==null||!c.isPersonalPrincipal()||c.getPersonalAccountId()==null) throw new BusinessException("Personal authentication is required.");return c.getPersonalAccountId();}
    private void markFailed(UUID account,UUID id,String msg){String m=msg==null?"RAG processing failed.":msg; try { jdbc.update("UPDATE PERSONAL_RAG_DOCUMENTS SET status=?,error_message=?,updated_at=? WHERE id=? AND personal_account_id=?",DocumentStatus.FAILED.name(),m.substring(0,Math.min(2000,m.length())),LocalDateTime.now(),id,account); } catch(Exception updateError) { log.error("Unable to mark Personal RAG document {} as FAILED",id,updateError); }}
    private KnowledgeBaseResponse mapKb(java.sql.ResultSet rs)throws java.sql.SQLException{return KnowledgeBaseResponse.builder().id(rs.getObject("id",UUID.class)).name(rs.getString("name")).description(rs.getString("description")).status(KnowledgeBaseStatus.valueOf(rs.getString("status"))).embeddingProvider(rs.getString("embedding_provider")).embeddingModel(rs.getString("embedding_model")).vectorStore(rs.getString("vector_store")).chunkingStrategy(ChunkingStrategy.valueOf(rs.getString("chunking_strategy"))).createdAt(rs.getTimestamp("created_at").toLocalDateTime()).updatedAt(rs.getTimestamp("updated_at").toLocalDateTime()).build();}
    private DocumentResponse mapDoc(java.sql.ResultSet rs)throws java.sql.SQLException{return DocumentResponse.builder().id(rs.getObject("id",UUID.class)).knowledgeBaseId(rs.getObject("knowledge_base_id",UUID.class)).fileName(rs.getString("file_name")).contentType(rs.getString("content_type")).fileSizeBytes(rs.getObject("file_size_bytes",Long.class)).checksumSha256(rs.getString("checksum_sha256")).status(DocumentStatus.valueOf(rs.getString("status"))).chunkCount(rs.getInt("chunk_count")).errorMessage(rs.getString("error_message")).createdAt(rs.getTimestamp("created_at").toLocalDateTime()).updatedAt(rs.getTimestamp("updated_at").toLocalDateTime()).build();}
    private String trim(String s){return blank(s)?null:s.trim();} private boolean blank(String s){return s==null||s.isBlank();}
    private String safeFileName(String s){if(blank(s))throw new BusinessException("Document file name is required.");String n=Paths.get(s).getFileName().toString().trim();if(blank(n)||n.contains(".."))throw new BusinessException("Invalid document file name.");return n;}
    private String sha256(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
