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
import java.io.InputStream;

public class DaiyouseiInputStream extends InputStream {
    private final BencodeDecoder bd;
    private byte[] chunk = new byte[0];
    private int pos;

    public DaiyouseiInputStream(BencodeDecoder bd) {
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
