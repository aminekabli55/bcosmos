package com.bcosmos.backend.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code app_setting}; only {@link JpaAppSettingStore} should use it. */
public interface AppSettingJpaRepository extends JpaRepository<AppSettingEntity, String> {
}
