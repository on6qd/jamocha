package cc.javastudio.demo;

import cc.javastudio.jamocha.Autowired;
import cc.javastudio.jamocha.Component;

@Component
public class UserAccountClientComponent {

    private UserService userService;
    private AccountService accountService;

    public UserAccountClientComponent(UserService userService, AccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    public void displayUserAccount(){
        String userName = userService.getUserName();
        Long accountNumber = accountService.getAccountNumber(userName);
        System.out.println("\n\tUser Name: " + userName + "\n\tAccount Number: " + accountNumber);
    }

}
