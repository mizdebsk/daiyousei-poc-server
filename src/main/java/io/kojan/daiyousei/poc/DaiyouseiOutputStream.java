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
import java.io.OutputStream;

public class DaiyouseiOutputStream extends OutputStream {
    private final BencodeEncoder be;
    private final String label;
    private byte[] buf = new byte[500];
    private int pos;

    public DaiyouseiOutputStream(BencodeEncoder be, String label) {
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
