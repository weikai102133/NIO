package test;

import NIO.NIOSocketServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class NIOSocketServerTest{
    ServerSocketChannel serverSocketChannel;
    SocketChannel socketChannel;
    Selector selector;
    private SelectionKey selectionKey ;

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


    @Before
    public void setUp() throws Exception {
        initserver();
        initClient();
        selector.select();
        System.out.println("初始化完成");
        Set<SelectionKey> keySet = selector.selectedKeys();
        Iterator<SelectionKey> iter = keySet.iterator();
        while (iter.hasNext()){
            SelectionKey next= iter.next();
            iter.remove();
            if (next.isAcceptable()){
                selectionKey = next;
                ServerSocketChannel serverSocketChannel = (ServerSocketChannel)next.channel();
                SocketChannel socketChannel = serverSocketChannel.accept();
                socketChannel.configureBlocking(false);
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                buffer.put("a".getBytes());
                buffer.flip();
                socketChannel.write(buffer);
                System.out.println(buffer.toString());

            }
        }

    }

    @After
    public void tearDown() throws Exception {

    }


    private void initserver() throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        selector = Selector.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(8888));
        serverSocketChannel.register(selector,SelectionKey.OP_ACCEPT,new BufferManager());
    }

    private void initClient() throws IOException {
        InetSocketAddress inetSocketAddress = new InetSocketAddress(8888);
        Selector selector = Selector.open();
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(inetSocketAddress);
        socketChannel.register(selector, SelectionKey.OP_CONNECT);

    }
    private static byte[] intToBytes(int value) {
        byte[] result = new byte[4];
        for (int i = 0; i < 4; i++) {
            result[i] = (byte)((value >> (4-1-i)*8) & 0xff);
        }
        return result;
    }

    @Test
    public void getBytesFromObject() throws Exception {
        NIOSocketServer nioSocketServer = new NIOSocketServer();
        byte[] bytes = nioSocketServer.getBytesFromObject("a");
        for (int i = 0; i < bytes.length; i++) {
            System.out.println(bytes[i]);
        }
    }

    @Test
    public void deserialize() throws Exception {
        NIOSocketServer nioSocketServer = new NIOSocketServer();
        byte[] bytes = nioSocketServer.getBytesFromObject("a");
        Object o = nioSocketServer.deserialize(bytes);
        assertEquals(o.toString(),"a");
    }

    @Test
    public void byteArraytToInt() {
        NIOSocketServer nioSocketServer = new NIOSocketServer();
        byte[] bytes = nioSocketServer.intToBytes(9);
        int res = nioSocketServer.byteArraytToInt(bytes);
        assertEquals(res,9);
    }

    @Test
    public void read() throws IOException {
        selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
        selector.select();
        Set<SelectionKey> keySet = selector.selectedKeys();
        Iterator<SelectionKey> iter = keySet.iterator();
        while (iter.hasNext()){
            selectionKey = iter.next();
            iter.remove();
            if (selectionKey.isReadable()){
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                ServerSocketChannel serverSocketChannel = (ServerSocketChannel)selectionKey.channel();
                SocketChannel socketChannel = serverSocketChannel.accept();
                socketChannel.read(buffer);
                System.out.println(buffer.toString());
            }
        }

    }
}