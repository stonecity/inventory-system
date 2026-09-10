package com.dream.inventory.repository;

import com.dream.inventory.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {

    Optional<SysPermission> findByCode(String code);
}
