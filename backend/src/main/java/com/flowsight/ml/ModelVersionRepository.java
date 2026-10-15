package com.flowsight.ml;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, UUID> {

    Optional<ModelVersion> findByCapabilityAndVersion(String capability, String version);

    @Modifying
    @Query(value = """
        INSERT INTO model_versions (capability, version) VALUES (:capability, :version)
        ON CONFLICT (capability, version) DO NOTHING
        """, nativeQuery = true)
    int recordSeen(@Param("capability") String capability, @Param("version") String version);
}
