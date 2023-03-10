/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.docker.workflow.client;

import org.jenkinsci.plugins.docker.workflow.DockerTestUtil;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import hudson.util.VersionNumber;
import org.jenkinsci.plugins.docker.commons.fingerprint.ContainerRecord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class DockerClientTest {

    private DockerClient dockerClient;
    
    @Before
    public void setup() throws Exception {
        DockerTestUtil.assumeDocker();

        // Set stuff up for the test
        TaskListener taskListener = StreamTaskListener.fromStderr();
        Launcher.LocalLauncher launcher = new Launcher.LocalLauncher(taskListener);

        dockerClient = new DockerClient(launcher, null, null);
    }

    @Test
    public void test_run() throws IOException, InterruptedException {
        // Pin to a specific sha256 hash of the image to avoid any potential issues with the image changing in the future.
        // Original image tag: docker:20.10.9-dind
        String image = "docker.io/library/docker@sha256:d842418d21545fde57c2512681d9bdc4ce0e54f2e0305a293ee20a9b6166932b";
        EnvVars launchEnv = DockerTestUtil.newDockerLaunchEnv();
        String containerId =
                dockerClient.run(launchEnv, image, null, null, Collections.<String, String>emptyMap(), Collections.<String>emptyList(), new EnvVars(),
                        dockerClient.whoAmI(), "cat");
        Assert.assertEquals(64, containerId.length());
        ContainerRecord containerRecord = dockerClient.getContainerRecord(launchEnv, containerId);
        Assert.assertEquals(dockerClient.inspect(launchEnv, image, ".Id"), containerRecord.getImageId());
        Assert.assertTrue(containerRecord.getContainerName().length() > 0);
        Assert.assertTrue(containerRecord.getHost().length() > 0);
        Assert.assertTrue(containerRecord.getCreated() > 1000000000000L);

        // Check that an anonymous volume was created mounted at /var/lib/docker
        Assert.assertEquals(Collections.<String>singletonList("/var/lib/docker"), dockerClient.getVolumes(launchEnv, containerId));
        String anonymousVolumeName = dockerClient.inspect(launchEnv, containerId, "range .Mounts }}{{ .Name }}{{ end");
        Assert.assertEquals(64, anonymousVolumeName.length());

        // Also test that the stop works and cleans up after itself
        Assert.assertNotNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
        dockerClient.stop(launchEnv, containerId);
        Assert.assertNull(dockerClient.inspect(launchEnv, containerId, ".Name"));
        // Check that the anonymous volume was removed
        Assert.assertNull(dockerClient.inspect(launchEnv, anonymousVolumeName, ".Name"));
    }

    @Test
    public void test_valid_version() {
        VersionNumber dockerVersion = DockerClient.parseVersionNumber("Docker version 1.5.0, build a8a31ef");
        Assert.assertFalse(dockerVersion.isOlderThan(new VersionNumber("1.1")));
        Assert.assertFalse(dockerVersion.isOlderThan(new VersionNumber("1.5")));
        Assert.assertTrue(dockerVersion.isOlderThan(new VersionNumber("1.10")));
    }
    
    @Test
    public void test_invalid_version() {
        Assert.assertNull(DockerClient.parseVersionNumber("xxx"));
    }
}
