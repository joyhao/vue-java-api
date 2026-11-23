package com.joykit.modular.auth.mapping;

import com.joykit.modular.auth.service.AuthService;
import com.joykit.modular.user.po.UserPO;
import org.springframework.stereotype.Component;

@Component
public class AuthMapping {
    public AuthService.Identity poToIdentity(UserPO user, String clientType) {
        return new AuthService.Identity(user.getId(), user.getAccount(), user.getName(), clientType);
    }
}
