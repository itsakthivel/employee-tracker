package com.example.tracker.sensitive;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface SensitiveAccessLogRepository
    extends JpaRepository<SensitiveAccessLog, UUID>, JpaSpecificationExecutor<SensitiveAccessLog> {}
