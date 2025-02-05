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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class BencodeDecoder {
    private final SocketChannel sc;
    private final ByteBuffer buf = ByteBuffer.allocate(5);

    public BencodeDecoder(SocketChannel sc) {
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

    public void consume(String str) throws IOException {
        String s = Integer.valueOf(str.length()).toString() + ":" + str;
        for (int b : s.getBytes(StandardCharsets.UTF_8)) {
            consume(b);
        }
    }

    public String decodeUTF8() throws IOException {
        return new String(decodeString(), StandardCharsets.UTF_8);
    }

    public byte[] decodeString() throws IOException {
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

    public void decodeListStart() throws IOException {
        consume('l');
    }

    public void decodeListEnd() throws IOException {
        consume('e');
    }

    public boolean hasString() throws IOException {
        int b = peek();
        return b >= '0' && b <= '9';
    }
}
