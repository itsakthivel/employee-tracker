package com.example.tracker.reference;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, UUID> {
  boolean existsByNameIgnoreCase(String name);

  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  List<Location> findAllByOrderByNameAsc();
}
