package com.autoparts.inventory.service;

import com.autoparts.inventory.dto.AddPartRequest;
import com.autoparts.inventory.dto.CompatibleVehicle;
import com.autoparts.inventory.dto.InventoryItemResponse;
import com.autoparts.inventory.dto.UpdatePartRequest;
import com.autoparts.inventory.entity.InventoryItem;
import com.autoparts.inventory.enums.VehicleCategory;
import com.autoparts.inventory.repository.InventoryItemRepository;
import com.autoparts.inventory.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock InventoryItemRepository items;
    @Mock UserRepository users;
    @Mock NotificationService notifier;

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private InventoryService svc() {
        return new InventoryService(items, users, notifier);
    }

    private static CompatibleVehicle vehicle(String make, String model, Integer from, Integer to) {
        CompatibleVehicle v = new CompatibleVehicle();
        v.setMake(make);
        v.setModel(model);
        v.setYearFrom(from);
        v.setYearTo(to);
        return v;
    }

    @Test
    void addPersistsCompatibleVehiclesAndEchoesThemBack() {
        AddPartRequest req = new AddPartRequest();
        req.setPartName("Brake Pad");
        req.setVehicleCategory(VehicleCategory.FOUR_WHEELER);
        req.setQuantity(5);
        req.setCompatibleVehicles(List.of(
                vehicle("Maruti Suzuki", "Swift", 2011, 2017),
                vehicle("Hyundai", "i20", 2014, null)));
        when(items.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(users.findById(USER)).thenReturn(Optional.empty());

        InventoryItemResponse out = svc().add(USER, req);

        assertEquals(2, out.getCompatibleVehicles().size());
        assertEquals("Swift", out.getCompatibleVehicles().get(0).getModel());
        assertEquals(2017, out.getCompatibleVehicles().get(0).getYearTo());
    }

    @Test
    void addDefaultsToEmptyListWhenOmitted() {
        AddPartRequest req = new AddPartRequest();
        req.setPartName("Air Filter");
        req.setVehicleCategory(VehicleCategory.TWO_WHEELER);
        req.setQuantity(1);
        when(items.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(users.findById(USER)).thenReturn(Optional.empty());

        InventoryItemResponse out = svc().add(USER, req);

        assertTrue(out.getCompatibleVehicles().isEmpty());
    }

    @Test
    void updateReplacesTheListOnlyWhenProvided() {
        InventoryItem existing = new InventoryItem();
        existing.setId(UUID.randomUUID());
        existing.setUserId(USER);
        existing.setActive(true);
        existing.setPartName("Clutch Plate");
        existing.setCompatibleVehicles(List.of(vehicle("Tata", "Nano", 2009, 2018)));
        when(items.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(items.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdatePartRequest noChange = new UpdatePartRequest();
        noChange.setBrand("Valeo");
        InventoryItemResponse afterNoChange = svc().update(USER, existing.getId(), noChange);
        assertEquals(1, afterNoChange.getCompatibleVehicles().size());

        UpdatePartRequest replace = new UpdatePartRequest();
        replace.setCompatibleVehicles(List.of(
                vehicle("Mahindra", "Bolero", 2011, null),
                vehicle("Mahindra", "Scorpio", 2014, null)));
        InventoryItemResponse afterReplace = svc().update(USER, existing.getId(), replace);
        assertEquals(2, afterReplace.getCompatibleVehicles().size());
        assertEquals("Bolero", afterReplace.getCompatibleVehicles().get(0).getModel());
    }
}
