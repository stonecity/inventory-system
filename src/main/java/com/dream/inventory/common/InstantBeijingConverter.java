package com.dream.inventory.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 将实体中的 {@link Instant} 在 JDBC 边界上按 Asia/Shanghai 与 DATETIME 列互转。
 *
 * <p>背景：Hibernate 7 在 Instant ↔ DATETIME 的映射上不会应用
 * {@code hibernate.jdbc.time_zone}，导致 Instant 被按 UTC 壁钟值写入 DATETIME，
 * 任何直接查看数据库的客户端（包括 Navicat）看到的就不是北京时间。
 *
 * <p>本转换器以 {@code autoApply = true} 全局生效，所有实体的 Instant 字段
 * 在写入时被转成北京壁钟时 LocalDateTime，读取时再还原为 Instant。
 * 业务代码（{@code Instant.now()}、Instant 参数的 VO / 控制器 / 前端 JSON 协议）
 * 均无需改动，DTO 仍是 UTC ISO，前端 {@code ImsTime.format} 继续按北京时区渲染。
 */
@Converter(autoApply = true)
public class InstantBeijingConverter implements AttributeConverter<Instant, LocalDateTime> {

    @Override
    public LocalDateTime convertToDatabaseColumn(Instant attribute) {
        return attribute == null ? null : LocalDateTime.ofInstant(attribute, TimeZones.ZONE);
    }

    @Override
    public Instant convertToEntityAttribute(LocalDateTime dbData) {
        return dbData == null ? null : dbData.atZone(TimeZones.ZONE).toInstant();
    }
}