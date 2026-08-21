package com.ai.gateway.rag.ingestion;

import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class TikaDocumentParser implements DocumentParser {

    private final RagIngestionProperties properties;
    private final Tika tika = new Tika();

    @Override
    public boolean supports(String fileName, String contentType) {
        String extension = extension(fileName);
        if (extension != null && properties.getAllowedExtensions().contains(extension)) {
            return true;
        }

        if (contentType == null || contentType.isBlank()) {
            return false;
        }

        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.equals("application/pdf")
                || normalized.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || normalized.equals("text/plain")
                || normalized.equals("text/markdown")
                || normalized.equals("text/html")
                || normalized.equals("application/json")
                || normalized.equals("text/csv");
    }

    @Override
    public ParsedDocument parse(Path file, String fileName, String contentType) {
        if (!Files.exists(file)) {
            throw new DocumentIngestionException("Temporary document file does not exist.");
        }

        try {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            if (contentType != null && !contentType.isBlank()) {
                metadata.set(Metadata.CONTENT_TYPE, contentType);
            }

            String detectedType;
            try (InputStream inputStream = Files.newInputStream(file)) {
                detectedType = tika.detect(inputStream, metadata);
            }

            try (InputStream inputStream = Files.newInputStream(file)) {
                AutoDetectParser parser = new AutoDetectParser();
                BodyContentHandler handler = new BodyContentHandler(
                        properties.getMaxExtractedCharacters());
                ParseContext context = new ParseContext();
                parser.parse(inputStream, handler, metadata, context);

                String text = handler.toString();
                if (text == null || text.isBlank()) {
                    throw new DocumentIngestionException(
                            "Document contains no extractable text: " + fileName);
                }

                return new ParsedDocument(text, detectedType);
            }
        } catch (DocumentIngestionException ex) {
            throw ex;
        } catch (SAXException | TikaException ex) {
            throw new DocumentIngestionException(
                    "Unable to extract text from document: " + fileName, ex);
        } catch (Exception ex) {
            throw new DocumentIngestionException(
                    "Unable to read document: " + fileName, ex);
        }
    }

    private String extension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
