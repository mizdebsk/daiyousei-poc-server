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
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public class Main {
    public static void main(String[] args) throws IOException {
        String sockPath = System.getenv("DAIYOUSEI_UNIX_SOCKET");
        if (sockPath == null) {
            sockPath = "/tmp/daiyousei.socket";
            System.err.println("DAIYOUSEI_UNIX_SOCKET was not set, defaulting to " + sockPath);
        }
        CountDownLatch cdl = new CountDownLatch(1);
        Server.runServer(Path.of(sockPath), cdl);
    }
}
