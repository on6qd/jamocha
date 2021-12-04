package cc.javastudio.demo;

import cc.javastudio.jamocha.Component;
import cc.javastudio.jamocha.Qualifier;

@Component
public class AccountServiceImpl2 implements AccountService{
    @Override
    public Long getAccountNumber(String userName) {
        return 987654321L;
    }
}
