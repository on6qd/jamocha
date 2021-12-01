package cc.javastudio.demo;

import cc.javastudio.jamocha.Component;

@Component
public class UserServiceImpl implements UserService {

    @Override
    public String getUserName() {
        return "John Doe";
    }
}
