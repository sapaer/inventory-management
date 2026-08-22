package com.autoparts.inventory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiEnvelope<T> {
    private final boolean success;
    private final T data;
    private final ErrorInfo error;
    private final MetaInfo meta;

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
