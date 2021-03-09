/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.dosgi.itests.multi;

import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemPackage;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.cm.ConfigurationAdminOptions;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;

public class AbstractDosgiTest {
    static final int ZK_PORT = 35101;
    static final int HTTP_PORT = 8989;
    static final String HTTP_HOST = "localhost"; // can specify specific bound IP
    static final String HTTP_BASE_URI = "http://" + HTTP_HOST + ":" + HTTP_PORT;
    private static final int TIMEOUT = 20;

    @Inject
    BundleContext bundleContext;

    @BeforeClass
    public static void log() {
        System.out.println("-----------------------------------------------------------------");
    }

    public <T> T tryTo(String message, Callable<T> func) throws TimeoutException {
        return tryTo(message, func, 5000);
    }

    public <T> T tryTo(String message, Callable<T> func, long timeout) throws TimeoutException {
        Throwable lastException = null;
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                T result = func.call();
                if (result != null) {
                    return result;
                }
                lastException = null;
            } catch (Throwable e) {
                lastException = e;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                continue;
            }
        }
        TimeoutException ex = new TimeoutException("Timeout while trying to " + message);
        if (lastException != null) {
            ex.addSuppressed(lastException);
        }
        throw ex;
    }

    /**
     * Sleeps for a short interval, throwing an exception if timeout has been reached. Used to facilitate a
     * retry interval with timeout when used in a loop.
     *
     * @param startTime the start time of the entire operation in milliseconds
     * @param timeout the timeout duration for the entire operation in seconds
     * @param message the error message to use when timeout occurs
     * @throws InterruptedException if interrupted while sleeping
     */
    private static void sleepOrTimeout(long startTime, long timeout, String message)
        throws InterruptedException, TimeoutException {
        timeout *= 1000; // seconds to millis
        long elapsed = System.currentTimeMillis() - startTime;
        long remaining = timeout - elapsed;
        if (remaining <= 0) {
            throw new TimeoutException(message);
        }
        long interval = Math.min(remaining, 1000);
        Thread.sleep(interval);
    }

    @SuppressWarnings({
                       "rawtypes", "unchecked"
    })
    protected ServiceReference waitService(BundleContext bc, Class cls, String filter, int timeout)
        throws Exception {
        System.out.println("Waiting for service: " + cls + " " + filter);
        long startTime = System.currentTimeMillis();
        while (true) {
            Collection refs = bc.getServiceReferences(cls, filter);
            if (refs != null && refs.size() > 0) {
                return (ServiceReference)refs.iterator().next();
            }
            sleepOrTimeout(startTime, timeout, "Service not found: " + cls + " " + filter);
        }
    }

    protected void waitPort(int port) throws Exception {
        System.out.println("Waiting for server to appear on port: " + port);
        long startTime = System.currentTimeMillis();
        while (true) {
            Socket s = null;
            try {
                s = new Socket((String)null, port);
                // yep, its available
                System.out.println("Port: " + port + " is listening now");
                return;
            } catch (IOException e) {
                sleepOrTimeout(startTime, TIMEOUT, "Timeout waiting for port " + port);
            } finally {
                if (s != null) {
                    try {
                        s.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
    }

    protected Bundle getBundleByName(BundleContext bc, String name) {
        for (Bundle bundle : bc.getBundles()) {
            if (bundle.getSymbolicName().equals(name)) {
                return bundle;
            }
        }
        return null;
    }

    protected static int getFreePort() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true); // enables quickly reopening socket on same port
            socket.bind(new InetSocketAddress(0)); // zero finds a free port
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    protected void waitWebPage(String urlSt) throws InterruptedException, TimeoutException {
        System.out.println("Waiting for url " + urlSt);
        HttpURLConnection con = null;
        long startTime = System.currentTimeMillis();
        while (true) {
            try {
                URL url = new URL(urlSt);
                con = (HttpURLConnection)url.openConnection();
                int status = con.getResponseCode();
                if (status == 200) {
                    return;
                }
            } catch (ConnectException e) {
                // Ignore connection refused
            } catch (MalformedURLException e) {
                throw new RuntimeException(e.getMessage(), e);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
            sleepOrTimeout(startTime, TIMEOUT, "Timeout waiting for web page " + urlSt);
        }
    }

    protected void assertBundlesStarted() {
        for (Bundle bundle : bundleContext.getBundles()) {
            System.out
                .println(bundle.getSymbolicName() + ":" + bundle.getVersion() + ": " + bundle.getState());
            if (bundle.getState() != Bundle.ACTIVE) {
                try {
                    bundle.start();
                } catch (BundleException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected ZooKeeper createZookeeperClient() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ZooKeeper zk = new ZooKeeper("localhost:" + ZK_PORT, 1000, event -> {
            if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                latch.countDown();
            }
        });
        latch.await(5000, TimeUnit.MILLISECONDS);
        return zk;
    }

    protected void assertNodeExists(ZooKeeper zk, String zNode, int timeout) {
        long endTime = System.currentTimeMillis() + timeout;
        Stat stat = null;
        while (stat == null && System.currentTimeMillis() < endTime) {
            try {
                stat = zk.exists(zNode, null);
                Thread.sleep(200);
            } catch (Exception e) {
                // Ignore
            }
        }
        Assert.assertNotNull("ZooKeeper node " + zNode + " was not found", stat);
    }

    protected static Option configHttpService(String host, int port) {
        return newConfiguration("org.ops4j.pax.web")
            .put("org.osgi.service.http.port", "" + port)
            .put("org.ops4j.pax.web.listening.addresses", host)
            .asOption();
    }

    protected static Option configZKConsumer() {
        return newConfiguration("org.apache.aries.rsa.discovery.zookeeper") //
            .put("zookeeper.host", "127.0.0.1") //
            .put("zookeeper.port", "" + ZK_PORT).asOption();
    }

    protected static Option configZKServer() {
        return newConfiguration("org.apache.aries.rsa.discovery.zookeeper.server")
            .put("clientPort", "" + ZK_PORT) //
            .asOption();
    }

    protected static Option configLogging() {
        File dir = new File(MultiBundleTools.getRootDirectory(), "src/test/resources/cfg");
        return ConfigurationAdminOptions.configurationFolder(dir);
    }

    protected static MavenArtifactProvisionOption taskServiceAPI() {
        return mavenBundle().groupId("org.apache.cxf.dosgi.samples")
            .artifactId("cxf-dosgi-samples-soap-api").versionAsInProject();
    }

    protected static MavenArtifactProvisionOption taskServiceImpl() {
        return mavenBundle().groupId("org.apache.cxf.dosgi.samples")
            .artifactId("cxf-dosgi-samples-soap-impl").versionAsInProject();
    }

    protected static MavenArtifactProvisionOption taskRESTAPI() {
        return mavenBundle().groupId("org.apache.cxf.dosgi.samples")
            .artifactId("cxf-dosgi-samples-rest-api").versionAsInProject();
    }

    protected static MavenArtifactProvisionOption taskRESTImpl() {
        return mavenBundle().groupId("org.apache.cxf.dosgi.samples")
            .artifactId("cxf-dosgi-samples-rest-impl").versionAsInProject();
    }

    protected static Option basicTestOptions() {
        return composite(CoreOptions.junitBundles(), //
                         MultiBundleTools.getDistro(), //
                         // javax.xml.soap is imported since CXF 3.3.0 (CXF-7872, commit a95593cf),
                         // so we must add it to mutli-bundle distro, but then we get a
                         // conflict with the one exported by framework system bundle (version $JDK)
                         // in this test, and removing a system bundle is a mess, so instead we export
                         // it again with our desired version number so everyone is happy
                         systemPackage("javax.xml.soap;version=1.4.0"),
                         // avoids "ClassNotFoundException: org.glassfish.jersey.internal.RuntimeDelegateImpl"
                         systemProperty("javax.ws.rs.ext.RuntimeDelegate")
                             .value("org.apache.cxf.jaxrs.impl.RuntimeDelegateImpl"),

                         mavenBundle("org.ops4j.pax.tinybundles", "tinybundles").versionAsInProject(),
                         mavenBundle("biz.aQute.bnd", "biz.aQute.bndlib").versionAsInProject(),

                         systemProperty("org.osgi.service.http.port").value("" + HTTP_PORT),
                         systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("INFO"), //
                         systemProperty("pax.exam.osgi.unresolved.fail").value("true"), //
                         systemProperty("org.apache.cxf.stax.allowInsecureParser").value("true"), //
                         systemProperty("rsa.export.policy.filter").value("(name=cxf)"), //
                         configHttpService(HTTP_HOST, HTTP_PORT),
                         configLogging()
        );
    }

    protected static VMOption debug() {
        return CoreOptions.vmOption("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005");
    }

}
