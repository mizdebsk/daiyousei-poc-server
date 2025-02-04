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
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CatApp implements Application {
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
