package com.dream.inventory.service;

import com.dream.inventory.entity.SysSetting;
import com.dream.inventory.repository.SysSettingRepository;
import com.dream.inventory.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SysSettingRepository settingRepository;

    public Map<String, String> getAll() {
        Map<String, String> map = new LinkedHashMap<>();
        settingRepository.findAll().forEach(s -> map.put(s.getSettingKey(), s.getSettingValue()));
        return map;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> update(Map<String, String> settings) {
        Long userId = SecurityUtils.currentUser() != null ? SecurityUtils.currentUser().getId() : null;
        settings.forEach((key, value) -> {
            SysSetting s = settingRepository.findById(key).orElse(SysSetting.builder().settingKey(key).build());
            s.setSettingValue(value);
            s.setUpdatedBy(userId);
            settingRepository.save(s);
        });
        return getAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public void seedDefaultsIfEmpty() {
        if (settingRepository.count() > 0) return;
        List<SysSetting> defaults = List.of(
                setting("reserve.expire.hours", "48", "销售预留超时小时数"),
                setting("stocktake.warn.hours", "72", "盘点锁定超时告警小时数"),
                setting("stocktake.auto-cancel.days", "7", "盘点超时自动取消天数"),
                setting("doc.seq.width", "6", "单号日序列位数"));
        settingRepository.saveAll(defaults);
    }

    private SysSetting setting(String key, String value, String desc) {
        return SysSetting.builder().settingKey(key).settingValue(value).description(desc).build();
    }
}
