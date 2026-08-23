package com.ai.gateway.rag.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordAwareDocumentChunkerTest {

    private RecordAwareDocumentChunker chunker() {
        RagIngestionProperties properties = new RagIngestionProperties();
        properties.setMaxChunks(20);
        return new RecordAwareDocumentChunker(new ObjectMapper(), properties);
    }

    @Test
    void shouldCreateOneChunkPerTopLevelRecord() {
        List<DocumentChunk> chunks = chunker().chunk("""
                [
                  {"recordId":"TBA-001","company":"Klarna","industry":"Fintech"},
                  {"recordId":"TBA-002","company":"Stripe","industry":"Payments"}
                ]
                """);

        assertEquals(2, chunks.size());
        assertEquals("TBA-001", chunks.get(0).recordId());
        assertEquals("TBA-002", chunks.get(1).recordId());
        assertEquals("TBA-001-chunk-0", chunks.get(0).chunkId());
        assertTrue(chunks.get(0).metadataJson().contains("\"recordId\":\"TBA-001\""));
        assertTrue(chunks.get(0).metadataJson().contains("\"chunkingStrategy\":\"RECORD_AWARE\""));
    }

    @Test
    void shouldExtractRecordsFromRecordsContainer() {
        List<DocumentChunk> chunks = chunker().chunk("""
                {
                  "records": [
                    {"id":"R-1","name":"First"},
                    {"id":"R-2","name":"Second"}
                  ]
                }
                """);

        assertEquals(2, chunks.size());
        assertEquals("R-1", chunks.get(0).recordId());
        assertTrue(chunks.get(0).content().contains("Name: First"));
    }

    @Test
    void shouldGenerateStableFallbackRecordId() {
        List<DocumentChunk> chunks = chunker().chunk("""
                [{"company":"Unknown"}]
                """);

        assertEquals("record-0", chunks.get(0).recordId());
        assertTrue(chunks.get(0).content().contains("Company: Unknown"));
    }

    @Test
    void shouldFlattenNestedObjectsAndArraysIntoCanonicalText() {
        List<DocumentChunk> chunks = chunker().chunk("""
                [{
                  "recordId":"R-9",
                  "architecture":{"database":"PostgreSQL","nodes":7},
                  "tags":["FIN","SCALE"]
                }]
                """);

        String content = chunks.get(0).content();

        assertTrue(content.contains("Architecture Database: PostgreSQL"));
        assertTrue(content.contains("Architecture Nodes: 7"));
        assertTrue(content.contains("Tags: FIN"));
        assertTrue(content.contains("Tags: SCALE"));
        assertFalse(content.contains("\"architecture\""));
    }

    @Test
    void shouldRejectInvalidJson() {
        assertThrows(
                DocumentIngestionException.class,
                () -> chunker().chunk("{not-valid-json"));
    }

    @Test
    void shouldRejectPrimitiveRecords() {
        assertThrows(
                DocumentIngestionException.class,
                () -> chunker().chunk("[\"not-an-object\"]"));
    }
}
