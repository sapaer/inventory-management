package com.autoparts.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MetaInfo {
    private final int page;
    private final int limit;
    private final long total;
}
