package com.mesaayuda.plantillas;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlantillaAreaRepository extends JpaRepository<PlantillaArea, UUID> {

    Optional<PlantillaArea> findByNombreAndAreaIdAndActivaTrue(String nombre, UUID areaId);

    Optional<PlantillaArea> findByNombreAndAreaId(String nombre, UUID areaId);

    List<PlantillaArea> findByAreaId(UUID areaId);
}
