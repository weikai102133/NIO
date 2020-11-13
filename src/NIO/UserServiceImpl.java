package NIO;

import rpcCommon.IUserService;
import rpcCommon.User;

public class UserServiceImpl implements IUserService {
    @Override
    public User findUserById(Integer id){
        return new User(id,"Alice");
    }

}
