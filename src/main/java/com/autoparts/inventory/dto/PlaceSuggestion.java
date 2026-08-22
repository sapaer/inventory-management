package com.autoparts.inventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaceSuggestion {
    private final String placeId;
    private final String description;
    private final String mainText;
    private final String secondaryText;
}
