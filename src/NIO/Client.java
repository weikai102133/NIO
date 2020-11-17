package NIO;



import rpcCommon.IProductService;
import rpcCommon.IUserService;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Client {
    private static Selector selector;
    private static SocketChannel socketChannel;

    public static void main(String[] args) throws IOException {
        selector = Selector.open();
        socketChannel = SocketChannel.open();
        String ip = "localhost";
        int port = 8888;
        Stub stub = new Stub(selector,socketChannel,ip,port);
        stub.init();
        stub.start();
        IProductService service = (IProductService)stub.getStub(IProductService.class, ProductServiceImpl.class);
        System.out.println("client收到"+service.findProductById(10023));
        System.out.println("第二次客户端远程调用");
        IUserService service1 = (IUserService) stub.getStub(IUserService.class, UserServiceImpl.class);
        System.out.println("client收到"+service1.findUserById(10023));
        System.out.println("第三次客户端远程调用");
        IProductService service2 = (IProductService)stub.getStub(IProductService.class, ProductServiceImpl.class);
        System.out.println("client收到"+service2.findProductById(10023));
    }
}
