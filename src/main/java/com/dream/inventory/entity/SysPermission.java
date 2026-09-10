package com.dream.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sys_permission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SysPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 50)
    private String module;

    @Column(name = "parent_id")
    private Long parentId;
}
