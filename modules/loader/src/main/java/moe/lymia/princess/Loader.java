/*
 * Copyright (c) 2015-2016 Lymia Alusyia <lymia@lymiahugs.com>
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

package moe.lymia.princess;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

class LoaderException extends RuntimeException {
    LoaderException(String message, Throwable cause) {
        super(message, cause);
    }
}

class LoaderData {
    final String mainClass;
    final String[] classPath;

    LoaderData(String mainClass, String[] classPath) {
        this.mainClass = mainClass;
        this.classPath = classPath;
    }
}

public final class Loader {
    private static RuntimeException error(String error, Exception e) {
        JOptionPane.showMessageDialog(null,
                "Could not start PrincessEdit:\n" + error, "PrincessEdit",
                JOptionPane.ERROR_MESSAGE);
        if (e != null) e.printStackTrace();
        return new LoaderException(error, e);
    }

    private Path getExecutablePath() {
        try {
            URL sourceURL = Loader.class.getProtectionDomain().getCodeSource().getLocation();
            return Paths.get(sourceURL.toURI());
        } catch (Exception e) {
            throw error("Could not find executable directory.", e);
        }
    }

    private String getOS() {
        String nameProp = System.getProperty("os.name");
        String archProp = System.getProperty("os.arch");

        String os;
        if (nameProp.equals("Linux")) os = "linux";
        else if (nameProp.startsWith("Mac ")) os = "macos";
        else if (nameProp.startsWith("Windows ")) os = "windows";
        else throw error("Your operating system (" + nameProp + ") is not supported.", null);

        String arch;
        if (archProp.equals("amd64") || archProp.equals("x86_64")) arch = "x86_64";
        else if (archProp.equals("x86") || archProp.matches("^i?86$")) arch = "x86";
        else if (archProp.equals("aarch64")) arch = "aarch64";
        else throw error("Your system architecture (" + archProp + ") is not supported.", null);

        return os + "-" + arch;
    }

    private LoaderData getLoaderData(Path rootPath) {
        String os = getOS();

        try {
            Path inputPath = rootPath.resolve("manifest.properties");
            Properties prop = new Properties();
            prop.load(Files.newInputStream(inputPath));

            String mainClass = prop.getProperty("main");
            String classPath = prop.getProperty(os+".classpath");

            if (classPath == null)
                throw error("Your system configuration (" + os + ") is not supported in this build.", null);

            return new LoaderData(mainClass, classPath.split(":"));
        } catch (Exception e) {
            throw error("Could not load library manifest.", e);
        }
    }

    private Class<?> loadMainClass(Path rootPath) {
        LoaderData data = getLoaderData(rootPath);

        try {
            URL[] urls = new URL[data.classPath.length];
            for (int i = 0; i < data.classPath.length; i++)
                urls[i] = rootPath.resolve(data.classPath[i]).toUri().toURL();
            return new URLClassLoader(urls).loadClass(data.mainClass);
        } catch (Exception e) {
            throw error("Failed to load main class.", e);
        }
    }

    private void start(String[] args) throws InvocationTargetException {
        if (System.getProperty("org.graalvm.nativeimage.kind") != null) {
            throw error("Loader is not designed for use with GraalVM native-image.", null);
        }

        Path bin = getExecutablePath();
        Path dir = bin.getParent();
        System.setProperty("moe.lymia.princess.startedFromLoader", "true");
        System.setProperty("moe.lymia.princess.loaderBinary", bin.toUri().toString());
        System.setProperty("moe.lymia.princess.rootDirectory", dir.toUri().toString());
        System.setProperty("moe.lymia.princess.libDirectory", dir.resolve("lib").toUri().toString());
        Class<?> main = loadMainClass(dir.resolve("lib"));

        Method m;

        try {
            m = main.getMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            throw error("Main class does not declare a main method.", e);
        }

        try {
            m.invoke(null, new Object[]{args});
        } catch (IllegalAccessException e) {
            throw error("Main class is not accessible.", e);
        }
    }

    public static void main(String[] args) throws InvocationTargetException {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Warning: Failed to set system Look and Feel.");
            e.printStackTrace();
        }

        new Loader().start(args);
    }
}
