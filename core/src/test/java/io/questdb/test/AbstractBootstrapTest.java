/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2023 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.test;

import io.questdb.Bootstrap;
import io.questdb.ServerMain;
import io.questdb.cairo.CairoEngine;
import io.questdb.cairo.wal.ApplyWal2TableJob;
import io.questdb.cairo.wal.CheckWalTransactionsJob;
import io.questdb.std.Files;
import io.questdb.std.str.Path;
import io.questdb.test.tools.TestUtils;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class AbstractBootstrapTest {

    @ClassRule
    public static final TemporaryFolder temp = new TemporaryFolder();
    protected static final int HTTP_MIN_PORT = 9011;
    protected static final int HTTP_PORT = 9010;
    protected static final int ILP_BUFFER_SIZE = 4 * 1024;
    protected static final int ILP_PORT = 9009;
    protected static final Properties PG_CONNECTION_PROPERTIES = new Properties();
    protected static final int PG_PORT = 8822;
    protected static final String PG_CONNECTION_URI = getPgConnectionUri(PG_PORT);
    private static final File siteDir = new File(Objects.requireNonNull(ServerMain.class.getResource("/io/questdb/site/")).getFile());
    protected static CharSequence root;
    private static boolean publicZipStubCreated = false;

    @Rule
    public TestName testName = new TestName();

    @BeforeClass
    public static void setUpStatic() throws Exception {
        // fake public.zip if it's missing to avoid forcing use of build-web-console profile just to run tests
        URL resource = ServerMain.class.getResource("/io/questdb/site/public.zip");
        if (resource == null) {
            File publicZip = new File(siteDir, "public.zip");
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(publicZip))) {
                ZipEntry entry = new ZipEntry("test.txt");
                zip.putNextEntry(entry);
                zip.write("test".getBytes());
                zip.closeEntry();
            }
            publicZipStubCreated = true;
        }
        try {
            root = temp.newFolder(UUID.randomUUID().toString()).getAbsolutePath();
        } catch (IOException e) {
            throw new ExceptionInInitializerError();
        }
    }

    @AfterClass
    public static void tearDownStatic() throws Exception {
        if (publicZipStubCreated) {
            File publicZip = new File(siteDir, "public.zip");
            if (publicZip.exists()) {
                publicZip.delete();
            }
        }
        Path path = Path.getThreadLocal(root);
        Files.rmdir(path.slash$());
        temp.delete();
    }

    protected static void createDummyConfiguration(
            int httpPort,
            int httpMinPort,
            int pgPort,
            int ilpPort,
            String... extra) throws Exception {
        final String confPath = root.toString() + Files.SEPARATOR + "conf";
        TestUtils.createTestPath(confPath);
        String file = confPath + Files.SEPARATOR + "server.conf";
        try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {

            // enable services
            writer.println("http.enabled=true");
            writer.println("http.min.enabled=true");
            writer.println("pg.enabled=true");
            writer.println("line.tcp.enabled=true");
            writer.println("line.udp.enabled=true");

            // disable services
            writer.println("http.query.cache.enabled=false");
            writer.println("pg.select.cache.enabled=false");
            writer.println("pg.insert.cache.enabled=false");
            writer.println("pg.update.cache.enabled=false");
            writer.println("cairo.wal.enabled.default=false");
            writer.println("metrics.enabled=false");
            writer.println("telemetry.enabled=false");

            // configure end points
            writer.println("http.bind.to=0.0.0.0:" + httpPort);
            writer.println("http.min.net.bind.to=0.0.0.0:" + httpMinPort);
            writer.println("pg.net.bind.to=0.0.0.0:" + pgPort);
            writer.println("line.tcp.net.bind.to=0.0.0.0:" + ilpPort);
            writer.println("line.udp.bind.to=0.0.0.0:" + ilpPort);
            writer.println("line.udp.receive.buffer.size=" + ILP_BUFFER_SIZE);

            // configure worker pools
            writer.println("shared.worker.count=2");
            writer.println("http.worker.count=1");
            writer.println("http.min.worker.count=1");
            writer.println("pg.worker.count=1");
            writer.println("line.tcp.writer.worker.count=1");
            writer.println("line.tcp.io.worker.count=1");

            // extra
            if (extra != null) {
                for (String s : extra) {
                    writer.println(s);
                }
            }
        }

        // mime types
        file = confPath + Files.SEPARATOR + "mime.types";
        try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
            writer.println("");
        }

        // logs
        file = confPath + Files.SEPARATOR + "log.conf";
        System.setProperty("out", file);
        try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
            writer.println("writers=stdout");
            writer.println("w.stdout.class=io.questdb.log.LogConsoleWriter");
            writer.println("w.stdout.level=INFO");
        }
    }

    protected static void createDummyConfiguration(String... extra) throws Exception {
        createDummyConfiguration(HTTP_PORT, HTTP_MIN_PORT, PG_PORT, ILP_PORT, extra);
    }

    protected static void drainWalQueue(CairoEngine engine) {
        try (final ApplyWal2TableJob walApplyJob = new ApplyWal2TableJob(engine, 1, 1, null)) {
            walApplyJob.drain(0);
            new CheckWalTransactionsJob(engine).run(0);
            // run once again as there might be notifications to handle now
            walApplyJob.drain(0);
        }
    }

    static String[] extendArgsWith(String[] args, String... moreArgs) {
        int argsLen = args.length;
        int extLen = moreArgs.length;
        int size = argsLen + extLen;
        if (size < 1) {
            Assert.fail("what are you trying to do?");
        }
        String[] extendedArgs = new String[size];
        System.arraycopy(args, 0, extendedArgs, 0, argsLen);
        System.arraycopy(moreArgs, 0, extendedArgs, argsLen, extLen);
        return extendedArgs;
    }

    protected static String getPgConnectionUri(int pgPort) {
        return "jdbc:postgresql://127.0.0.1:" + pgPort + "/qdb";
    }

    void assertFail(String message, String... args) {
        try {
            new Bootstrap(extendArgsWith(args, Bootstrap.SWITCH_USE_DEFAULT_LOG_FACTORY_CONFIGURATION));
            Assert.fail();
        } catch (Bootstrap.BootstrapException thr) {
            TestUtils.assertContains(thr.getMessage(), message);
        }
    }

    static {
        PG_CONNECTION_PROPERTIES.setProperty("user", "admin");
        PG_CONNECTION_PROPERTIES.setProperty("password", "quest");
        PG_CONNECTION_PROPERTIES.setProperty("sslmode", "disable");
        PG_CONNECTION_PROPERTIES.setProperty("binaryTransfer", "true");
    }
}
