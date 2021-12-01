package cc.javastudio.demo;

import cc.javastudio.jamocha.Autowired;
import cc.javastudio.jamocha.Component;

@Component
public class UserAccountClientComponent {

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    public void displayUserAccount(){
        String userName = userService.getUserName();
        Long accountNumber = accountService.getAccountNumber(userName);
        System.out.println("\n\tUser Name: " + userName + "\n\tAccount Number: " + accountNumber);
    }

}
