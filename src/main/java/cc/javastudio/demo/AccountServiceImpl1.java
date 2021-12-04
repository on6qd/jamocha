package cc.javastudio.demo;

import cc.javastudio.jamocha.Component;

@Component
public class AccountServiceImpl1 implements AccountService{
    @Override
    public Long getAccountNumber(String userName) {
        return 123456789L;
    }
}
