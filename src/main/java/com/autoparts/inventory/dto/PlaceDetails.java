package com.autoparts.inventory.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaceDetails {
    private final String address;
    private final String area;
    private final String city;
    private final String state;
    private final String pincode;
    private final Double geoLat;
    private final Double geoLng;
}
