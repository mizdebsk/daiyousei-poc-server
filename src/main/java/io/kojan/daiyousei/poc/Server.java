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
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class Server {
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
            BencodeEncoder be = new BencodeEncoder(sc);
            be.encodeListStart();
            be.flush();

            BencodeDecoder bd = new BencodeDecoder(sc);
            bd.decodeListStart();

            bd.consume("argv");
            bd.decodeListStart();
            String appName = bd.decodeUTF8();
            List<String> args = new ArrayList<>();
            while (bd.hasString()) {
                args.add(bd.decodeUTF8());
            }
            bd.decodeListEnd();

            bd.consume("cwd");
            Path cwd = Path.of(bd.decodeUTF8());

            bd.consume("env");
            bd.decodeListStart();
            Map<String, String> env = new LinkedHashMap<>();
            while (bd.hasString()) {
                env.put(bd.decodeUTF8(), bd.decodeUTF8());
            }
            bd.decodeListEnd();

            Application app =
                    switch (Path.of(appName).getFileName().toString()) {
                        case "whoami" -> new WhoamiApp();
                        case "cat" -> new CatApp();
                        default -> null;
                    };

            DaiyouseiInputStream in = new DaiyouseiInputStream(bd);
            PrintStream out = new PrintStream(new DaiyouseiOutputStream(be, "stdout"), true);
            PrintStream err = new PrintStream(new DaiyouseiOutputStream(be, "stderr"), true);
            try {
                System.err.println("Running App: args" + args + ", env=" + env + ", cwd=" + cwd);
                err.println("Running app: " + appName);
                int ret = app.run(args, env, cwd, in, out, err);
                System.err.println("App returned " + ret);
                out.close();
                err.close();
                be.encodeUTF8("exitcode");
                be.encodeInteger(ret);
                be.encodeListEnd();
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
