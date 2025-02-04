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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class BencodeEncoder implements Closeable {
    private final SocketChannel sc;
    private ByteBuffer buf = ByteBuffer.allocate(3);

    public BencodeEncoder(SocketChannel sc) {
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
