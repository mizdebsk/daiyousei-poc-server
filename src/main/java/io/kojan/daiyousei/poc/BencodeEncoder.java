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

    private void sendByte(int b) throws IOException {
        buf.put((byte) b);
        if (buf.remaining() == 0) {
            flush();
        }
    }

    private void sendBytes(byte[] bytes) throws IOException {
        for (byte b : bytes) {
            sendByte(b);
        }
    }

    public void encodeInteger(Integer i) throws IOException {
        sendByte('i');
        sendBytes(i.toString().getBytes(StandardCharsets.UTF_8));
        sendByte('e');
    }

    public void encodeString(byte[] data, int off, Integer len) throws IOException {
        sendBytes(len.toString().getBytes(StandardCharsets.UTF_8));
        sendByte(':');
        for (int i = off; i < off + len; i++) {
            sendByte(data[i]);
        }
    }

    public void encodeUTF8(String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        encodeString(bytes, 0, bytes.length);
    }

    public void encodeListStart() throws IOException {
        sendByte('l');
    }

    public void encodeListEnd() throws IOException {
        sendByte('e');
    }

    @Override
    public void close() throws IOException {
        flush();
        sc.close();
    }
}
