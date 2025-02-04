/*-
 * Copyright (c) 2025 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kojan.daiyousei.poc;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

interface App {
    int run(
            List<String> args,
            Map<String, String> env,
            Path cwd,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws Throwable;
}

class CatApp implements App {
    @Override
    public int run(
            List<String> args,
            Map<String, String> env,
            Path cwd,
            InputStream in,
            PrintStream out,
            PrintStream err) {
        if (args.isEmpty()) {
            try {
                in.transferTo(out);
            } catch (IOException e) {
                err.println("cat: Error reading stdin: " + e);
                return 1;
            }
        } else {
            for (String arg : args) {
                Path p = Path.of(arg);
                if (!p.isAbsolute()) {
                    p = cwd.resolve(p);
                }
                try (InputStream pin = Files.newInputStream(p)) {
                    pin.transferTo(out);
                } catch (IOException e) {
                    err.println("cat: Error reading " + p + ": " + e);
                    return 1;
                }
            }
        }
        return 0;
    }
}

class WhoamiApp implements App {
    @Override
    public int run(
            List<String> args,
            Map<String, String> env,
            Path cwd,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws Throwable {
        out.println("You are " + env.get("USER"));
        return 0;
    }
}

class BD {
    private final SocketChannel sc;
    private final ByteBuffer buf = ByteBuffer.allocate(5);

    public BD(SocketChannel sc) {
        this.sc = sc;
        buf.flip();
    }

    private void input() throws IOException {
        if (buf.remaining() == 0) {
            buf.flip();
            buf.limit(buf.capacity());
            System.err.println("Input: block, av=" + buf.limit());
            sc.read(buf);
            buf.flip();
            System.err.println("Input: read " + buf.remaining());
        }
    }

    private int peek() throws IOException {
        input();
        buf.mark();
        try {
            return buf.get();
        } finally {
            buf.reset();
        }
    }

    private byte recv() throws IOException {
        input();
        return buf.get();
    }

    private void consume(int b) throws IOException {
        if (recv() != b) {
            throw new RuntimeException("Protocol error");
        }
    }

    public void consume(int... bytes) throws IOException {
        for (int b : bytes) {
            consume(b);
        }
    }

    public String readUTF8() throws IOException {
        return new String(readBytes(), StandardCharsets.UTF_8);
    }

    public byte[] readBytes() throws IOException {
        if (!hasString()) {
            throw new RuntimeException("Protocol error");
        }
        int n = buf.get() - '0';
        while (hasString()) {
            n = 10 * n + buf.get() - '0';
        }
        consume(':');
        byte[] bytes = new byte[n];
        for (int i = 0; i < n; i++) {
            bytes[i] = recv();
        }
        System.err.println("Read bytes: " + n);
        return bytes;
    }

    public void readListStart() throws IOException {
        consume('l');
    }

    public void readListEnd() throws IOException {
        consume('e');
    }

    public boolean hasString() throws IOException {
        int b = peek();
        return b >= '0' && b <= '9';
    }
}

class IS extends InputStream {
    private final BD bd;
    private byte[] chunk = new byte[0];
    private int pos;

    public IS(BD bd) {
        this.bd = bd;
    }

    @Override
    public int read() throws IOException {
        while (pos == chunk.length) {
            if (!bd.hasString()) {
                return -1;
            }
            bd.consume('5', ':', 's', 't', 'd', 'i', 'n');
            chunk = bd.readBytes();
            pos = 0;
        }
        System.err.println("IS read(1) return " + chunk[pos]);
        return chunk[pos++];
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        while (pos == chunk.length) {
            if (!bd.hasString()) {
                return -1;
            }
            bd.consume('5', ':', 's', 't', 'd', 'i', 'n');
            chunk = bd.readBytes();
            pos = 0;
        }
        int n = Math.min(chunk.length - pos, len);
        System.arraycopy(chunk, pos, b, off, n);
        pos += n;
        return n;
    }
}

class BE implements Closeable {
    private final SocketChannel sc;
    private ByteBuffer buf = ByteBuffer.allocate(3);

    public BE(SocketChannel sc) {
        this.sc = sc;
    }

    public void flush() throws IOException {
        if (buf.position() > 0) {
            buf.flip();
            sc.write(buf);
            buf.flip();
        }
    }

    private void send(int b) throws IOException {
        buf.put((byte) b);
        if (buf.remaining() == 0) {
            flush();
        }
    }

    private void send(byte[] bytes) throws IOException {
        for (byte b : bytes) {
            send(b);
        }
    }

    public void addInteger(Integer i) throws IOException {
        send('i');
        send(i.toString().getBytes(StandardCharsets.UTF_8));
        send('e');
    }

    public void addBytes(byte[] data, int off, Integer len) throws IOException {
        send(len.toString().getBytes(StandardCharsets.UTF_8));
        send(':');
        for (int i = off; i < off + len; i++) {
            send(data[i]);
        }
    }

    public void addUTF8(String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        addBytes(bytes, 0, bytes.length);
    }

    public void startList() throws IOException {
        send('l');
    }

    public void endList() throws IOException {
        send('e');
    }

    @Override
    public void close() throws IOException {
        flush();
        sc.close();
    }
}

class OS extends OutputStream {
    private final BE be;
    private final String label;
    private byte[] buf = new byte[500];
    private int pos;

    public OS(BE be, String label) {
        this.be = be;
        this.label = label;
    }

    @Override
    public void write(int b) throws IOException {
        buf[pos++] = (byte) b;
        if (pos == buf.length) {
            flush();
        }
    }

    @Override
    public void flush() throws IOException {
        if (pos > 0) {
            be.addUTF8(label);
            be.addBytes(buf, 0, pos);
            pos = 0;
        }
        be.flush();
    }

    @Override
    public void close() throws IOException {
        flush();
    }
}

public class Server {
    public static void main(String[] args) throws IOException {
        String sockPath = System.getenv("DAIYOUSEI_UNIX_SOCKET");
        if (sockPath == null) {
            sockPath = "/tmp/daiyousei.socket";
            System.err.println("DAIYOUSEI_UNIX_SOCKET was not set, defaulting to " + sockPath);
        }
        CountDownLatch cdl = new CountDownLatch(1);
        runServer(Path.of(sockPath), cdl);
    }

    public static void runServer(Path socketPath, CountDownLatch cdl) throws IOException {
        ServerSocketChannel socket = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        Files.deleteIfExists(socketPath);
        socket.bind(UnixDomainSocketAddress.of(socketPath));
        System.err.println("Server started");
        cdl.countDown();
        while (true) {
            SocketChannel channel = socket.accept();
            System.err.println("Server accepted connection");
            Thread thread = new Thread(() -> accept(channel));
            thread.start();
        }
    }

    private static void accept(SocketChannel sc) {
        try {
            BE be = new BE(sc);
            be.startList();
            be.flush();

            BD bd = new BD(sc);
            bd.readListStart();

            bd.consume('4', ':', 'a', 'r', 'g', 'v');
            bd.readListStart();
            String appName = bd.readUTF8();
            List<String> args = new ArrayList<>();
            while (bd.hasString()) {
                args.add(bd.readUTF8());
            }
            bd.readListEnd();

            bd.consume('3', ':', 'c', 'w', 'd');
            Path cwd = Path.of(bd.readUTF8());

            bd.consume('3', ':', 'e', 'n', 'v');
            bd.readListStart();
            Map<String, String> env = new LinkedHashMap<>();
            while (bd.hasString()) {
                env.put(bd.readUTF8(), bd.readUTF8());
            }
            bd.readListEnd();

            App app =
                    switch (Path.of(appName).getFileName().toString()) {
                        case "whoami" -> new WhoamiApp();
                        case "cat" -> new CatApp();
                        default -> null;
                    };

            IS in = new IS(bd);
            PrintStream out = new PrintStream(new OS(be, "stdout"), true);
            PrintStream err = new PrintStream(new OS(be, "stderr"), true);
            try {
                System.err.println("Running App: args" + args + ", env=" + env + ", cwd=" + cwd);
                err.println("Running app: " + appName);
                int ret = app.run(args, env, cwd, in, out, err);
                System.err.println("App returned " + ret);
                out.close();
                err.close();
                be.addUTF8("exitcode");
                be.addInteger(ret);
                be.endList();
                be.close();
            } catch (Throwable t) {
                t.printStackTrace(err);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                sc.close();
                System.err.println("Closed socket");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
