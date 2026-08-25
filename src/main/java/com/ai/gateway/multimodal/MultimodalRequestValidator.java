package com.ai.gateway.multimodal;

import com.ai.gateway.dto.ChatRequest;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

@Component
public class MultimodalRequestValidator {

    private static final int MAX_MEDIA_ITEMS = 8;

    public void validate(ChatRequest request) {
        if (request == null) {
            return;
        }

        List<MediaContent> media = request.getMedia();

        if (media == null || media.isEmpty()) {
            return;
        }

        if (media.size() > MAX_MEDIA_ITEMS) {
            throw new MultimodalValidationException(
                    "A maximum of 8 media items is allowed per request.");
        }

        for (MediaContent item : media) {
            validateMediaItem(item);
        }
    }

    private void validateMediaItem(MediaContent item) {

        if (item == null) {
            throw new MultimodalValidationException(
                    "Media item must not be null.");
        }

        if (item.getType() == null || item.getSourceType() == null) {
            throw new MultimodalValidationException(
                    "Each media item must define type and sourceType.");
        }

        if (item.getMimeType() == null || item.getMimeType().isBlank()) {
            throw new MultimodalValidationException(
                    "Each media item must define mimeType.");
        }

        String mimeType = item.getMimeType()
                .trim()
                .toLowerCase();

        boolean hasUrl =
                item.getUrl() != null
                        && !item.getUrl().isBlank();

        boolean hasData =
                item.getData() != null
                        && !item.getData().isBlank();

        validateSource(item, hasUrl, hasData);

        validateMimeType(item, mimeType);

        if (item.getSourceType() == MediaSourceType.BASE64) {
            validateBase64(item.getData());
        }
    }

    private void validateSource(
            MediaContent item,
            boolean hasUrl,
            boolean hasData) {

        if (item.getSourceType() == MediaSourceType.URL) {

            if (!hasUrl) {
                throw new MultimodalValidationException(
                        "URL media requires url.");
            }

            if (hasData) {
                throw new MultimodalValidationException(
                        "URL media must not contain a base64 payload.");
            }

            return;
        }

        if (item.getSourceType() == MediaSourceType.BASE64) {

            if (!hasData) {
                throw new MultimodalValidationException(
                        "BASE64 media requires data.");
            }

            if (hasUrl) {
                throw new MultimodalValidationException(
                        "BASE64 media must not contain a URL.");
            }

            return;
        }

        throw new MultimodalValidationException(
                "Unsupported media source type.");
    }

    private void validateMimeType(
            MediaContent item,
            String mimeType) {

        if (item.getType() == MediaTypeKind.IMAGE
                && !mimeType.startsWith("image/")) {

            throw new MultimodalValidationException(
                    "IMAGE media requires an image/* MIME type.");
        }

        if (item.getType() == MediaTypeKind.AUDIO
                && !mimeType.startsWith("audio/")) {

            throw new MultimodalValidationException(
                    "AUDIO media requires an audio/* MIME type.");
        }
    }

    private void validateBase64(String data) {

        try {
            Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException ex) {

            throw new MultimodalValidationException(
                    "Invalid BASE64 media data.");
        }
    }
}