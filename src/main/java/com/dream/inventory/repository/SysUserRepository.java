package com.dream.inventory.repository;

import com.dream.inventory.entity.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("""
            SELECT u FROM SysUser u
            WHERE (:keyword IS NULL
                OR u.username LIKE CONCAT('%', :keyword, '%')
                OR u.realName LIKE CONCAT('%', :keyword, '%')
                OR u.phone LIKE CONCAT('%', :keyword, '%')
                OR u.email LIKE CONCAT('%', :keyword, '%'))
              AND (:status IS NULL OR u.status = :status)
              AND (:roleCode IS NULL OR EXISTS (
                  SELECT 1 FROM u.roles r WHERE r.code = :roleCode
              ))
            """)
    Page<SysUser> search(@Param("keyword") String keyword,
                         @Param("status") Integer status,
                         @Param("roleCode") String roleCode,
                         Pageable pageable);
}
