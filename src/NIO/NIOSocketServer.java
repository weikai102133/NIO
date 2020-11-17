package NIO;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


public class NIOSocketServer extends Thread {
    ServerSocketChannel  serverSocketChannel = null;
    Selector connectSelector = null;
    private ExecutorService selectorService;
    private ExecutorService work;
    private Selector[] ioSelectors;
    private HashMap<Selector,Object> ioSelectorsSyns;
    private AtomicInteger atomicInteger = new AtomicInteger(0);
    int selectorSize =Runtime.getRuntime().availableProcessors() + 1;

    public static void main(String[] args) throws Exception {
        NIOSocketServer server = new NIOSocketServer();
        server.initServer();
        //server.start();
    }

    public void initServer() throws IOException {
        connectSelector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(8888));

        selectorService = Executors.newFixedThreadPool(selectorSize + 1);
        work = Executors.newFixedThreadPool(16);
        ioSelectors = new Selector[selectorSize];
        ioSelectorsSyns =new HashMap<>(ioSelectors.length);
        for (int i = 0; i < ioSelectors.length; i++) {
            ioSelectors[i] =Selector.open();
            ioSelectorsSyns.put(ioSelectors[i],new Object());
        }
        selectorService.submit(new ConnectionSelector());
        for (int i = 0; i < ioSelectors.length; i++) {
            selectorService.submit(new IOSelector(ioSelectors[i]));
        }

    }

    class ConnectionSelector extends Thread{

        public ConnectionSelector() throws ClosedChannelException {
            serverSocketChannel.register(connectSelector,SelectionKey.OP_ACCEPT);
        }

        public void run(){
            while (true){
                try {
                    connectSelector.select();
                    Set<SelectionKey> selectionKeys = connectSelector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();

                    while (iterator.hasNext()){
                        SelectionKey selectionKey = iterator.next();
                        iterator.remove();
                        if (selectionKey.isAcceptable()){
                            accept(selectionKey);
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    class IOSelector extends Thread{
        private final Selector selector;

        public IOSelector(Selector selector){
            this.selector = selector;
        }

        public void run(){
            while (true){
                try{
                    synchronized (ioSelectorsSyns.get(selector)) {

                    }
                    if(selector.select() > 0){
                        Set<SelectionKey> keySet = selector.selectedKeys();
                        Iterator<SelectionKey> iter = keySet.iterator();
                        while (iter.hasNext()){
                            SelectionKey selectionKey = iter.next();
                            iter.remove();

                            if (selectionKey.isReadable()){
                                read(selectionKey);
                            }

                            if (selectionKey.isWritable()){
                                write(selectionKey);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class IOTask extends Thread{
        private SelectionKey selectionKey;
        private byte[] bodyByte;

        public IOTask(SelectionKey selectionKey,byte[] bodyByte){
            this.selectionKey = selectionKey;
            this.bodyByte = bodyByte;
        }

        public void run(){
            Object res = deserialize(bodyByte);
            BufferManager manager = (BufferManager)selectionKey.attachment();
            /*System.out.println(new Random().nextInt(100)+"receive from clien content is:" + new String(bodyByte));*/
            Object result = null;
            try {
                result = process(res);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            System.out.println(result);
            byte[] bytesFromObject = new byte[0];
            try {
                bytesFromObject = getBytesFromObject((Serializable) result);
            } catch (Exception e) {
                e.printStackTrace();
            }

            ByteBuffer buffer = ByteBuffer.allocate(bytesFromObject.length+4);
            buffer.put(intToBytes(bytesFromObject.length));
            buffer.put(bytesFromObject);

            if(manager.writeBuffer == null || manager.writeBuffer.remaining() == manager.writeBuffer.capacity()){
                buffer.flip();
                manager.writeBuffer.put(buffer);
                //selectionKey.attach(manager);
            }else {
                buffer.flip();
                if (manager.writeBuffer.remaining() < buffer.remaining()){
                    int oldCap = manager.writeBuffer.capacity();
                    ByteBuffer oldBuffer = manager.writeBuffer;
                    oldBuffer.flip();
                    int newSize = oldCap+buffer.remaining();
                    newSize = (int)(newSize + (newSize*0.2f));
                    manager.writeBuffer = ByteBuffer.allocate(newSize);
                    manager.writeBuffer.put(oldBuffer);
                }
                manager.writeBuffer.put(buffer);
                //selectionKey.attach(manager);
            }
            synchronized (ioSelectorsSyns.get(selectionKey.selector())){
                selectionKey.selector().wakeup();
                selectionKey.interestOps(SelectionKey.OP_WRITE);
            }

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
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
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

    private void accept(SelectionKey selectionKey) {
        try {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel)selectionKey.channel();
            SocketChannel socketChannel = serverSocketChannel.accept();
            System.out.println("is acceptable");
            socketChannel.configureBlocking(false);

            Selector selector = ioSelectors[atomicInteger.getAndIncrement() % selectorSize];
            synchronized (ioSelectorsSyns.get(selector)){
                selector.wakeup();
                socketChannel.register(selector,SelectionKey.OP_READ,new BufferManager());
            }
            System.out.println("connected");
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void write(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel = (SocketChannel)selectionKey.channel();
        socketChannel.configureBlocking(false);
        System.out.println("response from server to client");
        try {
            BufferManager manager = (BufferManager)selectionKey.attachment();
            ByteBuffer buffer = manager.writeBuffer;
            buffer.flip();
            socketChannel.write(buffer);
            if(!buffer.hasRemaining()){
                System.out.println("server写完");
                manager.setReadBuffer(ByteBuffer.allocate(1024).put(manager.readBuffer));
                manager.setWriteBuffer(ByteBuffer.allocate(1024));
                manager.setCacheBuffer(ByteBuffer.allocate(1024));
                //selectionKey.attach(manager);
                synchronized (ioSelectorsSyns.get(selectionKey.selector())){
                    selectionKey.selector().wakeup();
                    selectionKey.interestOps(SelectionKey.OP_READ);
                }
            }else {
                manager.setWriteBuffer(buffer);
                //selectionKey.attach(manager);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void read(SelectionKey selectionKey) {
        System.out.println("监听到read事件");
        int head_length = 4;
        byte[] headByte = new byte[4];
        try {
            SocketChannel channel = (SocketChannel)selectionKey.channel();
            channel.configureBlocking(false);
            BufferManager manager = (BufferManager)selectionKey.attachment();
            int bodyLen = -1;
            if(manager.isCache() == true){
                manager.readBuffer.clear();
                manager.cacheBuffer.flip();
                manager.readBuffer.put(manager.cacheBuffer);
                manager.cacheBuffer.clear();
            }
            channel.read(manager.readBuffer);
            manager.readBuffer.flip();
            //解决半包粘包
            while (manager.readBuffer.hasRemaining()){
                if (bodyLen == -1){
                    if(manager.readBuffer.remaining() >= head_length){
                        manager.readBuffer.mark();
                        manager.readBuffer.get(headByte);
                        bodyLen = byteArraytToInt(headByte);
                    }else {
                        manager.readBuffer.reset();
                        manager.cache = true;
                        manager.cacheBuffer.put(manager.readBuffer);
                        break;
                    }
                }else {
                    if (manager.readBuffer.remaining() >= bodyLen){
                        byte[] bodyByte = new byte[bodyLen];
                        manager.readBuffer.get(bodyByte,0,bodyLen);
                        manager.readBuffer.mark();
                        bodyLen = -1;
                        work.submit(new IOTask(selectionKey,bodyByte));
                    }else {
                        manager.readBuffer.reset();
                        manager.cacheBuffer.put(manager.readBuffer);
                        int oldCap = manager.readBuffer.remaining();
                        ByteBuffer oldBuffer = manager.readBuffer;
                        oldBuffer.flip();
                        int newSize = oldCap + bodyLen;
                        newSize = (int)(newSize + (newSize)*0.2f);
                        manager.readBuffer = ByteBuffer.allocate(newSize);
                        manager.readBuffer.put(oldBuffer);
                        manager.cache = true;
                        break;
                    }
                }
            }
            /*selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);*/
        } catch (Exception e) {
            try {
                serverSocketChannel.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    public static int byteArraytToInt(byte[] bytes){
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4-1-i)*8;
            value += (bytes[i] & 0x000000ff) << shift;
        }
        return value;
    }

    public static byte[] intToBytes(int value) {
        byte[] result = new byte[4];
        for (int i = 0; i < 4; i++) {
            result[i] = (byte)((value >> (4-1-i)*8) & 0xff);
        }
        return result;
    }

    private Object process(Object res) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        ArrayList<Object>  list = (ArrayList<Object>) res;
        String methodName = (String) list.get(0);
        Class clazz = (Class)list.get(1);
        Class[] parameterTypes = (Class[]) list.get(2);
        Object[] args = (Object[])list.get(3);
        Method method = clazz.getMethod(methodName,parameterTypes);
        Object o = method.invoke(clazz.newInstance(),args);
        return  o;
    }

    class BufferManager{
        private ByteBuffer readBuffer;
        private ByteBuffer writeBuffer;
        private ByteBuffer cacheBuffer;
        private volatile boolean cache = false;

        public ByteBuffer getReadBuffer() {
            return readBuffer;
        }

        public void setReadBuffer(ByteBuffer byteBuffer) {
            this.readBuffer = byteBuffer;
        }

        public ByteBuffer getWriteBuffer() {
            return writeBuffer;
        }

        public void setWriteBuffer(ByteBuffer writeBuffer) {
            this.writeBuffer = writeBuffer;
        }

        public ByteBuffer getCacheBuffer() {
            return cacheBuffer;
        }

        public void setCacheBuffer(ByteBuffer cacheBuffer) {
            this.cacheBuffer = cacheBuffer;
        }

        public boolean isCache() {
            return cache;
        }

        public void setCache(boolean cache) {
            this.cache = cache;
        }

        public BufferManager(){
            this.readBuffer = ByteBuffer.allocate(1024);
            this.cacheBuffer = ByteBuffer.allocate(1024);
            this.writeBuffer = ByteBuffer.allocate(1024);
        }
    }
}
