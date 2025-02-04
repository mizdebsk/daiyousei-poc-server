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

import static org.junit.jupiter.api.Assertions.fail;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ServerTest {

    SocketChannel ch;
    ByteBuffer bb = ByteBuffer.allocate(1);

    @BeforeEach
    public void setUp() throws Exception {
        Path socketPath = Path.of("/tmp/my.socket");
        CountDownLatch cdl = new CountDownLatch(1);
        Thread server =
                new Thread(
                        () -> {
                            try {
                                Server.runServer(socketPath, cdl);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
        server.setDaemon(true);
        server.start();
        cdl.await();
        ch = SocketChannel.open(StandardProtocolFamily.UNIX);
        ch.connect(UnixDomainSocketAddress.of(socketPath));
    }

    void send(int b) throws Exception {
        bb.put((byte) b);
        bb.flip();
        ch.write(bb);
        bb.flip();
    }

    int recv() throws Exception {
        if (ch.read(bb) < 0) {
            fail("Unexpected EOF");
        }
        bb.flip();
        int b = bb.get();
        bb.flip();
        return b;
    }

    void send(String... ss) throws Exception {
        for (String s : ss) {
            for (int b : s.getBytes(StandardCharsets.UTF_8)) {
                send(b);
            }
        }
    }

    void recv(String... ss) throws Exception {
        for (String s : ss) {
            for (int b : s.getBytes(StandardCharsets.UTF_8)) {
                int x = recv();
                if (b == x) {
                    System.out.println("RECV '" + (char) x + "'");
                } else {
                    System.out.println("RECV '" + (char) x + "', but wanted '" + (char) b + "'");
                    while (true) {
                        x = recv();
                        System.out.println("RECV '" + (char) x + "'");
                    }
                }
            }
        }
    }

    @Test
    public void testCatEmpty() throws Exception {
        recv("l");
        send("l");
        send("4:argv", "l", "3:cat", "e");
        send("3:cwd", "4:/tmp");
        send("3:env", "l", "3:FOO", "3:bar", "e");
        recv("6:stderr", "17:Running app: cat\n");
        send("e");
        recv("8:exitcode", "i0e");
        recv("e");
    }

    @Test
    public void testCatStdin() throws Exception {
        recv("l");
        send("l");
        send("4:argv", "l", "3:cat", "e");
        send("3:cwd", "4:/tmp");
        send("3:env", "l", "3:FOO", "3:bar", "e");
        recv("6:stderr", "17:Running app: cat\n");
        send("5:stdin", "7:Hello!\n");
        recv("6:stdout", "7:Hello!\n");
        send("5:stdin", "10:Good Bye!\n");
        recv("6:stdout", "10:Good Bye!\n");
        send("e");
        recv("8:exitcode", "i0e");
        recv("e");
    }

    @Test
    public void testCatNonexistent() throws Exception {
        recv("l");
        send("l");
        send("4:argv", "l", "3:cat", "18:/tmp/dummy-missing", "e");
        send("3:cwd", "4:/tmp");
        send("3:env", "l", "e");
        send("e");
        recv("6:stderr", "17:Running app: cat\n");
        recv(
                "6:stderr",
                "93:cat: Error reading /tmp/dummy-missing: java.nio.file.NoSuchFileException: /tmp/dummy-missing\n");
        recv("8:exitcode", "i1e");
        recv("e");
    }

    @Test
    public void testWhoami() throws Exception {
        recv("l");
        send("l");
        send("4:argv", "l", "6:whoami", "e");
        send("3:cwd", "4:/tmp");
        send("3:env", "l", "4:USER", "7:johndoe", "e");
        send("e");
        recv("6:stderr", "20:Running app: whoami\n");
        recv("6:stdout", "16:You are johndoe\n");
        recv("8:exitcode", "i0e");
        recv("e");
    }
}
