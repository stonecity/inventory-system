package com.dream.inventory.repository;

import com.dream.inventory.entity.SysSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysSettingRepository extends JpaRepository<SysSetting, String> {
}
