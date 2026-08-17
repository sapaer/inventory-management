package com.autoparts.inventory.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiEnvelope<T>(boolean success, T data, ErrorInfo error, MetaInfo meta) {
    public record ErrorInfo(String code, String message, String field) {}

    public record MetaInfo(int page, int limit, long total) {}

    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>(true, data, null, null);
    }

    public static <T> ApiEnvelope<T> error(String code, String message) {
        return new ApiEnvelope<>(false, null, new ErrorInfo(code, message, null), null);
    }

    public static <T> ApiEnvelope<T> error(String code, String message, String field) {
        return new ApiEnvelope<>(false, null, new ErrorInfo(code, message, field), null);
    }
}
