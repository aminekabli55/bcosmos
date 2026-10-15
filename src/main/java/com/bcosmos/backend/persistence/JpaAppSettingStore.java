package com.bcosmos.backend.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** {@link AppSettingStore} on top of Spring Data. Entities stay inside this class. */
@Repository
public class JpaAppSettingStore implements AppSettingStore {

    private final AppSettingJpaRepository settings;

    public JpaAppSettingStore(AppSettingJpaRepository settings) {
        this.settings = settings;
    }

    @Override
    public Optional<AppSettingRecord> find(String key) {
        return settings.findById(key).map(JpaAppSettingStore::toRecord);
    }

    @Override
    public AppSettingRecord put(String key, String value, Instant now) {
        AppSettingEntity entity = settings.findById(key).orElse(null);
        if (entity == null) {
            return toRecord(settings.save(new AppSettingEntity(key, value, now)));
        }
        entity.update(value, now);
        return toRecord(settings.save(entity));
    }

    @Override
    public void delete(String key) {
        settings.deleteById(key);
    }

    private static AppSettingRecord toRecord(AppSettingEntity entity) {
        return new AppSettingRecord(entity.getSettingKey(), entity.getSettingValue(), entity.getUpdatedAt());
    }
}
