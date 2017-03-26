/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.wrapper;

import java.io.Closeable;
import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class BootstrapMainStarter {
    public void start(String[] args, File gradleHome) throws Exception {
        URL[] bootstrapJars = findBootstrapJars(gradleHome);
        URLClassLoader contextClassLoader = new URLClassLoader(bootstrapJars, ClassLoader.getSystemClassLoader().getParent());
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        Class<?> mainClass = contextClassLoader.loadClass("org.gradle.launcher.GradleMain");
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, new Object[]{args});
        if (contextClassLoader instanceof Closeable) {
            ((Closeable) contextClassLoader).close();
        }
    }

    private URL[] findBootstrapJars(File gradleHome) throws MalformedURLException {
        URL[] bootstrapJars = new URL[2];
        for (File file : new File(gradleHome, "lib").listFiles()) {
            if (file.getName().matches("gradle-launcher-.*\\.jar")) {
                bootstrapJars[0] = file.toURI().toURL();
            } else if (file.getName().matches("gradle-tooling-api-provider-.*\\.jar")) {
                bootstrapJars[1] = file.toURI().toURL();
            }
        }
        if (bootstrapJars[0] == null || bootstrapJars[1] == null) {
            throw new RuntimeException(String.format("Could not locate the Gradle bootstrap JARs in Gradle distribution '%s'.", gradleHome));
        }
        return bootstrapJars;
    }
}
