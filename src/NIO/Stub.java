package NIO;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Stub extends Thread{
    private volatile SocketChannel socketChannel;
    private volatile Selector selector;
    private final ByteBuffer cacheBuffer = ByteBuffer.allocate(1024);
    private ByteBuffer byteBuffer = ByteBuffer.allocate(100);
    volatile boolean cache = false;
    volatile boolean write = false;
    volatile boolean resultSuccess = false;
    volatile boolean connect = false;
    ArrayList<Object> list = new ArrayList<>();
    private volatile AtomicInteger atomicInteger = new AtomicInteger(0);
    private  Object res;

    public Stub(Selector selector, SocketChannel socketChannel, String ip, int port) throws IOException {
        this.socketChannel =socketChannel;
        this.selector = selector;
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(ip,port));
    }

    public  Object getStub(Class clazz,Class impl) {
        InvocationHandler h = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                byteBuffer.clear();
                cacheBuffer.clear();
                list.clear();
                list.add(method.getName());
                list.add(impl);
                list.add(method.getParameterTypes());
                list.add(args);
                write = true;
                if (atomicInteger.get() > 0){
                    synchronized (selector){
                        socketChannel.register(selector,SelectionKey.OP_WRITE);
                    }
                }
                connect = true;
                while (!resultSuccess){

                }
                resultSuccess = false;
                return res;
            }
        };
        Object o = Proxy.newProxyInstance(clazz.getClassLoader(),new Class[]{clazz},h);
        return o;
    }

    public void init() throws IOException {
        synchronized (selector) {
            socketChannel.register(selector, SelectionKey.OP_CONNECT);
        }
    }

    public void run(){
        while (true){
            try {
                if(connect){
                    if(selector.select() > 0){
                            Set<SelectionKey> keySet = selector.selectedKeys();
                            Iterator<SelectionKey> iter = keySet.iterator();
                            while (iter.hasNext()){
                                SelectionKey selectionKey = iter.next();
                                iter.remove();

                                if (selectionKey.isConnectable()){
                                    finishConnect(selectionKey);
                                }

                                if (selectionKey.isWritable()){
                                    if (write){
                                        send(selectionKey);
                                    }
                                }

                                if (selectionKey.isReadable()){
                                    read(selectionKey);
                                }
                            }
                        }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void read(SelectionKey selectionKey) throws IOException {
        System.out.println("客户端监听到read事件");
        int head_length = 4;
        byte[] headBytes = new byte[4];

        SocketChannel socketChannel = (SocketChannel)selectionKey.channel();
        socketChannel.configureBlocking(false);
        int bodyLen = -1;
        if (cache){
            byteBuffer.clear();
            cacheBuffer.flip();
            byteBuffer.put(cacheBuffer);
            cacheBuffer.clear();
        }
        int len = socketChannel.read(byteBuffer);
        byteBuffer.flip();
        while (byteBuffer.hasRemaining()){
            if (bodyLen == -1){
                if (byteBuffer.remaining() >= head_length){
                    byteBuffer.mark();
                    byteBuffer.get(headBytes);
                    bodyLen = byteArraytToInt(headBytes);
                }else {
                    byteBuffer.reset();
                    cache = true;
                    cacheBuffer.put(byteBuffer);
                }
            }else {
                if (byteBuffer.remaining() >= bodyLen){
                    byte[] bodyByte = new byte[bodyLen];
                    byteBuffer.get(bodyByte,0,bodyLen);
                    byteBuffer.mark();
                    bodyLen = -1;
                    res = deserialize(bodyByte);
                    resultSuccess = true;
                    //System.out.println("receive from server content is: "+res);
                    connect = false;
                }else {
                    byteBuffer.reset();
                    cacheBuffer.put(byteBuffer);
                    int oldCap = byteBuffer.remaining();
                    ByteBuffer oldBuffer = byteBuffer;
                    oldBuffer.flip();
                    int newSize = oldCap + bodyLen;
                    newSize = (int)(newSize + (newSize)*0.2f);
                    byteBuffer = ByteBuffer.allocate(newSize);
                    byteBuffer.put(oldBuffer);
                    cache = true;
                    break;
                }
            }

        }
        selectionKey.interestOps(SelectionKey.OP_READ);
    }

    private void send(SelectionKey selectionKey) throws Exception {
        //System.out.println("客户端准备发送数据");
        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
        socketChannel.configureBlocking(false);
        byte[] bytes = getBytesFromObject(list);
        int head = bytes.length;
        ByteBuffer byteBuffer = ByteBuffer.allocate(4+head);
        byteBuffer.put(intToBytes(head));
        byteBuffer.put(bytes);
        byteBuffer.flip();
        while (byteBuffer.hasRemaining()){
            try {
                socketChannel.write(byteBuffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("客户端发送完毕");
        write = false;
        try {
            synchronized (selector) {
                socketChannel.register(selector, SelectionKey.OP_READ);
            }
        } catch (ClosedChannelException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static byte[] getBytesFromObject(Serializable obj) throws Exception {
        if (obj == null) {
            return null;
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bo);
        oos.writeObject(obj);
        return bo.toByteArray();
    }

    public static Object deserialize(byte[] bytes) {
        Object object = null;
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);//
            ObjectInputStream ois = new ObjectInputStream(bis);
            object = ois.readObject();
            ois.close();
            bis.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        }
        return object;
    }

    public static int byteArraytToInt(byte[] bytes){
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4-1-i);
            value += (bytes[i] & 0x000000ff) << shift;
        }
        return value;
    }

    private static byte[] intToBytes(int value) {
        byte[] result = new byte[4];
        for (int i = 0; i < 4; i++) {
            result[i] = (byte)((value >> (4-1-i)*8) & 0xff);
        }
        return result;
    }

    private void finishConnect(SelectionKey selectionKey) {
        atomicInteger.getAndIncrement();
        System.out.println("client finish connect!");
        SocketChannel socketChannel = (SocketChannel)selectionKey.channel();
        try {
            socketChannel.finishConnect();
            synchronized (selector){
                socketChannel.register(selector,SelectionKey.OP_WRITE);
                selectionKey.interestOps(SelectionKey.OP_WRITE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
