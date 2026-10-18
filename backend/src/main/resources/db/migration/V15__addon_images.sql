-- V15: optional product/add-on image stored as a bounded data URI.
ALTER TABLE gym_addon ADD COLUMN image_data TEXT;
ALTER TABLE gym_addon ADD CONSTRAINT gym_addon_image_data_size CHECK (image_data IS NULL OR length(image_data) <= 2800000);
