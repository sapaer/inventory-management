package com.autoparts.inventory.service;

import com.autoparts.inventory.config.AppProperties;
import com.autoparts.inventory.dto.PlaceDetails;
import com.autoparts.inventory.dto.PlaceSuggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PlacesService {
    private static final Logger log = LoggerFactory.getLogger(PlacesService.class);
    private static final String NOMINATIM_UA = "PartNear/1.0 (inventory-management)";
    private static final PlaceDetails EMPTY_DETAILS = new PlaceDetails(null, null, null, null, null, null, null);

    private final RestTemplate http = new RestTemplate();
    private final String googleKey;

    public PlacesService(AppProperties props) {
        this.googleKey = props.getGoogle() == null ? "" : blankToEmpty(props.getGoogle().getPlacesApiKey());
    }

    public List<PlaceSuggestion> autocomplete(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        if (googleEnabled()) {
            try {
                return googleAutocomplete(q);
            } catch (Exception ex) {
                log.warn("google autocomplete failed, using nominatim: {}", ex.getMessage());
            }
        }
        return nominatimAutocomplete(q);
    }

    public PlaceDetails details(String placeId) {
        if (placeId == null || placeId.isBlank()) {
            return EMPTY_DETAILS;
        }
        if (placeId.startsWith("nom:")) {
            String[] parts = placeId.substring(4).split(",", 2);
            if (parts.length == 2) {
                return reverse(parseDouble(parts[0]), parseDouble(parts[1]));
            }
        }
        if (googleEnabled()) {
            try {
                return googleDetails(placeId);
            } catch (Exception ex) {
                log.warn("google place details failed: {}", ex.getMessage());
            }
        }
        return EMPTY_DETAILS;
    }

    public PlaceDetails reverse(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return EMPTY_DETAILS;
        }
        if (googleEnabled()) {
            try {
                return googleReverse(lat, lng);
            } catch (Exception ex) {
                log.warn("google reverse geocode failed, using nominatim: {}", ex.getMessage());
            }
        }
        return nominatimReverse(lat, lng);
    }

    private boolean googleEnabled() {
        return !googleKey.isBlank();
    }

    @SuppressWarnings("unchecked")
    private List<PlaceSuggestion> googleAutocomplete(String q) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://maps.googleapis.com/maps/api/place/autocomplete/json")
                .queryParam("input", q)
                .queryParam("components", "country:in")
                .queryParam("language", "en")
                .queryParam("key", googleKey)
                .build()
                .encode()
                .toUri();
        Map<String, Object> body = http.getForObject(uri, Map.class);
        if (body == null) {
            return List.of();
        }
        String status = String.valueOf(body.getOrDefault("status", ""));
        if (!"OK".equals(status) && !"ZERO_RESULTS".equals(status)) {
            throw new IllegalStateException("Google Places status " + status);
        }
        List<Map<String, Object>> predictions = (List<Map<String, Object>>) body.getOrDefault("predictions", List.of());
        List<PlaceSuggestion> out = new ArrayList<>();
        for (Map<String, Object> p : predictions) {
            String mainText = null;
            String secondaryText = null;
            Map<String, Object> structured = (Map<String, Object>) p.get("structured_formatting");
            if (structured != null) {
                mainText = asString(structured.get("main_text"));
                secondaryText = asString(structured.get("secondary_text"));
            }
            out.add(new PlaceSuggestion(asString(p.get("place_id")), asString(p.get("description")), mainText, secondaryText));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private PlaceDetails googleDetails(String placeId) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://maps.googleapis.com/maps/api/place/details/json")
                .queryParam("place_id", placeId)
                .queryParam("fields", "formatted_address,geometry,address_component,name")
                .queryParam("key", googleKey)
                .build()
                .encode()
                .toUri();
        Map<String, Object> body = http.getForObject(uri, Map.class);
        if (body == null || !"OK".equals(String.valueOf(body.get("status")))) {
            throw new IllegalStateException("Google place details failed");
        }
        return fromGoogleResult((Map<String, Object>) body.get("result"));
    }

    @SuppressWarnings("unchecked")
    private PlaceDetails googleReverse(double lat, double lng) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://maps.googleapis.com/maps/api/geocode/json")
                .queryParam("latlng", lat + "," + lng)
                .queryParam("key", googleKey)
                .build()
                .encode()
                .toUri();
        Map<String, Object> body = http.getForObject(uri, Map.class);
        if (body == null || !"OK".equals(String.valueOf(body.get("status")))) {
            throw new IllegalStateException("Google reverse geocode failed");
        }
        List<Map<String, Object>> results = (List<Map<String, Object>>) body.getOrDefault("results", List.of());
        if (results.isEmpty()) {
            return EMPTY_DETAILS;
        }
        return fromGoogleResult(results.get(0));
    }

    @SuppressWarnings("unchecked")
    private PlaceDetails fromGoogleResult(Map<String, Object> result) {
        if (result == null) {
            return EMPTY_DETAILS;
        }
        String address = asString(result.get("formatted_address"));
        Double geoLat = null;
        Double geoLng = null;
        Map<String, Object> geometry = (Map<String, Object>) result.get("geometry");
        if (geometry != null) {
            Map<String, Object> point = (Map<String, Object>) geometry.get("location");
            if (point != null) {
                geoLat = toDouble(point.get("lat"));
                geoLng = toDouble(point.get("lng"));
            }
        }
        String area = firstComponent(result, "sublocality_level_1", "sublocality", "neighborhood", "route");
        String city = firstComponent(result, "locality", "administrative_area_level_2");
        String state = firstComponent(result, "administrative_area_level_1");
        String pincode = firstComponent(result, "postal_code");
        if (area == null) {
            area = result.get("name") == null ? city : String.valueOf(result.get("name"));
        }
        return new PlaceDetails(address, area, city, state, pincode, geoLat, geoLng);
    }

    @SuppressWarnings("unchecked")
    private String firstComponent(Map<String, Object> result, String... types) {
        List<Map<String, Object>> components = (List<Map<String, Object>>) result.get("address_components");
        if (components == null) {
            return null;
        }
        for (String wanted : types) {
            for (Map<String, Object> c : components) {
                List<String> have = (List<String>) c.getOrDefault("types", List.of());
                if (have.contains(wanted)) {
                    Object name = c.get("long_name");
                    return name == null ? null : String.valueOf(name);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<PlaceSuggestion> nominatimAutocomplete(String q) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://nominatim.openstreetmap.org/search")
                .queryParam("q", q)
                .queryParam("format", "json")
                .queryParam("addressdetails", "1")
                .queryParam("countrycodes", "in")
                .queryParam("limit", "6")
                .build()
                .encode()
                .toUri();
        List<Map<String, Object>> rows = nominatimGetList(uri);
        List<PlaceSuggestion> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String lat = String.valueOf(row.get("lat"));
            String lon = String.valueOf(row.get("lon"));
            String mainText = null;
            String secondaryText = null;
            Map<String, Object> address = (Map<String, Object>) row.get("address");
            if (address != null) {
                Object main = firstNonBlank(address.get("suburb"), address.get("neighbourhood"),
                        address.get("road"), address.get("village"), address.get("town"), address.get("city"));
                Object secondary = firstNonBlank(address.get("city"), address.get("state"));
                mainText = main == null ? null : String.valueOf(main);
                secondaryText = secondary == null ? null : String.valueOf(secondary);
            }
            out.add(new PlaceSuggestion("nom:" + lat + "," + lon, asString(row.get("display_name")), mainText, secondaryText));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private PlaceDetails nominatimReverse(double lat, double lng) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://nominatim.openstreetmap.org/reverse")
                .queryParam("lat", lat)
                .queryParam("lon", lng)
                .queryParam("format", "json")
                .queryParam("addressdetails", "1")
                .build()
                .encode()
                .toUri();
        Map<String, Object> body = nominatimGetMap(uri);
        if (body == null) {
            return EMPTY_DETAILS;
        }
        String address = asString(body.get("display_name"));
        String area = null;
        String city = null;
        String state = null;
        String pincode = null;
        Map<String, Object> addressParts = (Map<String, Object>) body.get("address");
        if (addressParts != null) {
            area = asString(firstNonBlank(addressParts.get("suburb"), addressParts.get("neighbourhood"),
                    addressParts.get("road"), addressParts.get("village"), addressParts.get("county")));
            city = asString(firstNonBlank(addressParts.get("city"), addressParts.get("town"),
                    addressParts.get("city_district"), addressParts.get("state_district")));
            state = asString(addressParts.get("state"));
            pincode = asString(firstNonBlank(addressParts.get("postcode")));
        }
        return new PlaceDetails(address, area, city, state, pincode, lat, lng);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> nominatimGetList(URI uri) {
        Map<String, Object>[] arr = nominatimExchange(uri, Map[].class);
        if (arr == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : arr) {
            out.add(row);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nominatimGetMap(URI uri) {
        return nominatimExchange(uri, Map.class);
    }

    private <T> T nominatimExchange(URI uri, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", NOMINATIM_UA);
        headers.set("Accept-Language", "en");
        return http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), type).getBody();
    }

    private static Object firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v != null && !String.valueOf(v).isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Double toDouble(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return parseDouble(String.valueOf(v));
    }

    private static Double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String blankToEmpty(String v) {
        return v == null ? "" : v.trim();
    }
}
