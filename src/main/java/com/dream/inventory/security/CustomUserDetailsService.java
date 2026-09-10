package com.dream.inventory.security;

import com.dream.inventory.common.BizException;
import com.dream.inventory.common.ErrorCode;
import com.dream.inventory.entity.SysUser;
import com.dream.inventory.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final SysUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        return new LoginUser(user);
    }

    public UserDetails loadUserById(Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "用户不存在或已失效"));
        return new LoginUser(user);
    }
}
