package com.autoparts.inventory.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PresignRequest {
    private String filename;
    private String contentType;
}
