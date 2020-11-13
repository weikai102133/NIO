package NIO;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;


public class NIOSocketServer extends Thread {
    ServerSocketChannel  serverSocketChannel = null;
    Selector selector= null;



    public void initServer() throws IOException {
        selector = Selector.open();
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(8888));
        serverSocketChannel.register(selector,SelectionKey.OP_ACCEPT);
    }

    public static void main(String[] args) throws Exception {
        NIOSocketServer server = new NIOSocketServer();
        server.initServer();
        server.start();
    }

    public void run(){
        while (true){
            try{
                if(selector.select() > 0){
                    Set<SelectionKey> keySet = selector.selectedKeys();
                    Iterator<SelectionKey> iter = keySet.iterator();
                    while (iter.hasNext()){
                        SelectionKey selectionKey = iter.next();
                        iter.remove();

                        if (selectionKey.isAcceptable()){
                            accpet(selectionKey);
                        }

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

    private void write(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel = (SocketChannel)selectionKey.channel();
        socketChannel.configureBlocking(false);
        System.out.println("response from server to client");
        try {
            BufferManager manager = (BufferManager)selectionKey.attachment();
            ByteBuffer buffer = manager.writeBuffer;
            buffer.flip();
            //System.out.println(buffer.position());
            socketChannel.write(buffer);
            if(!buffer.hasRemaining()){
                System.out.println("server写完");
                manager.setReadBuffer(ByteBuffer.allocate(1024));
                manager.setWriteBuffer(ByteBuffer.allocate(1024));
                manager.setCacheBuffer(ByteBuffer.allocate(1024));
                selectionKey.attach(manager);
                selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
            }else {
                manager.setWriteBuffer(buffer);
                selectionKey.attach(manager);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void read(SelectionKey selectionKey) {
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
                        Object res = deserialize(bodyByte);
                        /*System.out.println(new Random().nextInt(100)+"receive from clien content is:" + new String(bodyByte));*/
                        Object result = process(res);
                        System.out.println(result);
                        byte[] bytesFromObject = getBytesFromObject((Serializable) result);
                        ByteBuffer buffer = ByteBuffer.allocate(bytesFromObject.length+4);
                        buffer.put(intToBytes(bytesFromObject.length));
                        buffer.put(bytesFromObject);
                        bodyLen = -1;
                        if(manager.writeBuffer == null || manager.writeBuffer.remaining() == manager.writeBuffer.capacity()){
                            buffer.flip();
                            manager.writeBuffer.put(buffer);
                            selectionKey.attach(manager);
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
                            selectionKey.attach(manager);
                        }
                        selectionKey.interestOps(SelectionKey.OP_WRITE);
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
            selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
        } catch (IOException e) {
            try {
                serverSocketChannel.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Object process(Object res) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        ArrayList<Object>  list = (ArrayList<Object>) res;
        String methodName = (String) list.get(0);
        Class clazz = (Class)list.get(1);
        Class[] parameterTypes = (Class[]) list.get(2);
        Object[] args = (Object[])list.get(3);
        Method method = clazz.getMethod(methodName,parameterTypes);
        Object oo = method.invoke(clazz.newInstance(),args);
        return  oo;
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

    private void accpet(SelectionKey selectionKey) {
        try {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel)selectionKey.channel();
            SocketChannel socketChannel = serverSocketChannel.accept();
            System.out.println("is acceptable");
            socketChannel.configureBlocking(false);
            socketChannel.register(selector,SelectionKey.OP_READ,new BufferManager());
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        } catch (IOException e) {
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

    private static byte[] intToBytes(int value) {
        byte[] result = new byte[4];
        for (int i = 0; i < 4; i++) {
            result[i] = (byte)((value >> (4-1-i)*8) & 0xff);
        }
        return result;
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
