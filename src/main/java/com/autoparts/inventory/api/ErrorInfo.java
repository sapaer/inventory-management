package com.autoparts.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorInfo {
    private final String code;
    private final String message;
    private final String field;
}
