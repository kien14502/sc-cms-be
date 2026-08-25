package com.vnpt.mac.config;

import com.vnpt.mac.partner.entity.*;
import com.vnpt.mac.partner.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private final BootstrapAdminProperties properties; private final UserRepository users;
    private final RoleRepository roles; private final PasswordEncoder encoder;
    public BootstrapAdminRunner(BootstrapAdminProperties properties,UserRepository users,RoleRepository roles,PasswordEncoder encoder){this.properties=properties;this.users=users;this.roles=roles;this.encoder=encoder;}
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if(properties.email()==null||properties.email().isBlank()||properties.password()==null||properties.password().isBlank())return;
        if(users.existsByEmailIgnoreCase(properties.email()))return;
        var role=roles.findByCode(RoleCode.PLATFORM_ADMIN).orElseThrow();
        var user=UserEntity.invited(null,properties.email(),properties.fullName(),role);
        user.activate(encoder.encode(properties.password())); users.save(user);
    }
}
