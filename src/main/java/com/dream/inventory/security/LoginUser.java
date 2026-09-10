package com.dream.inventory.security;

import com.dream.inventory.entity.SysUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class LoginUser implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String realName;
    private final Integer status;
    private final Set<Long> warehouseIds;
    private final Set<String> roleCodes;
    private final Set<String> permissionCodes;
    private final Collection<? extends GrantedAuthority> authorities;

    public LoginUser(SysUser user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.realName = user.getRealName();
        this.status = user.getStatus();
        this.warehouseIds = user.getWarehouseIds() != null ? user.getWarehouseIds() : Set.of();
        this.roleCodes = user.getRoles().stream().map(r -> r.getCode()).collect(Collectors.toSet());
        this.permissionCodes = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(p -> p.getCode())
                .collect(Collectors.toSet());

        Set<GrantedAuthority> auths = new HashSet<>();
        roleCodes.forEach(code -> auths.add(new SimpleGrantedAuthority(code)));
        permissionCodes.forEach(code -> auths.add(new SimpleGrantedAuthority(code)));
        this.authorities = auths;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != null && status == 1;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isAccountNonLocked();
    }
}
