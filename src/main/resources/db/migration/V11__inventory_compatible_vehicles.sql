-- Vehicles a part is compatible with (make / model / variant / year range),
-- beyond the single vehicle_category already on the row. JSON array, same
-- pattern as inventory_items.images.
ALTER TABLE inventory_items ADD COLUMN compatible_vehicles JSON NOT NULL DEFAULT '[]';
