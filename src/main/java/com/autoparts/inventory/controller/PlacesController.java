package com.autoparts.inventory.controller;

import com.autoparts.inventory.api.ApiEnvelope;
import com.autoparts.inventory.dto.PlaceDetails;
import com.autoparts.inventory.dto.PlaceSuggestion;
import com.autoparts.inventory.service.PlacesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/places")
public class PlacesController {
    private final PlacesService places;

    public PlacesController(PlacesService places) {
        this.places = places;
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<ApiEnvelope<List<PlaceSuggestion>>> autocomplete(@RequestParam("q") String q) {
        return ResponseEntity.ok(ApiEnvelope.ok(places.autocomplete(q)));
    }

    @GetMapping("/details")
    public ResponseEntity<ApiEnvelope<PlaceDetails>> details(@RequestParam("placeId") String placeId) {
        return ResponseEntity.ok(ApiEnvelope.ok(places.details(placeId)));
    }

    @GetMapping("/reverse")
    public ResponseEntity<ApiEnvelope<PlaceDetails>> reverse(
            @RequestParam("lat") Double lat,
            @RequestParam("lng") Double lng
    ) {
        return ResponseEntity.ok(ApiEnvelope.ok(places.reverse(lat, lng)));
    }
}
