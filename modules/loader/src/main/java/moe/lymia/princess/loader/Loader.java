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

package moe.lymia.princess.loader;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

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
        JOptionPane.showMessageDialog(null, "Could not start MPPatch installer:\n"+error, "MPPatch Installer",
                                      JOptionPane.ERROR_MESSAGE);
        if(e != null) e.printStackTrace();
        return new RuntimeException(error, e);
    }

    private Path getExecutableDirectory() {
        try {
            String resourcePath = getClass().getPackage().toString().replace('.', '/')+"/marker.txt";
            URL resourceURL = getClass().getClassLoader().getResource(resourcePath);
            if(resourceURL == null) throw error("Marker resource not found.", null);
            JarURLConnection connection = (JarURLConnection) resourceURL.openConnection();
            return Paths.get(connection.getJarFileURL().toURI());
        } catch (Exception e) {
            throw error("Could not find executable directory.", e);
        }
    }

    private String getOS() {
        String nameProp = System.getProperty("os.name");
        String archProp = System.getProperty("os.arch");

        String os;
        if(nameProp.equals("Linux")) os = "linux";
        else if(nameProp.startsWith("Mac ")) os = "mac";
        else if(nameProp.startsWith("Windows ")) os = "win32";
        else throw error("Your operating system ("+nameProp+") is not supported.", null);

        String arch;
        if(archProp.equals("amd64") || archProp.equals("x86_64")) arch = "amd64";
        else if((archProp.equals("x86") || archProp.matches("^i?86$")) && !os.equals("mac")) arch = "x86";
        else throw error("Your system architecture ("+archProp+") is not supported.", null);

        return nameProp+"_"+archProp;
    }

    private LoaderData getLoaderData(Path rootPath) {
        String os = getOS();

        try {
            Path inputPath = rootPath.resolve("manifest.properties");
            Properties prop = new Properties();
            prop.load(Files.newInputStream(inputPath));

            String mainClass = prop.getProperty("main");
            String classPath = prop.getProperty(os);

            if(classPath == null)
                throw error("Your system configuration ("+os+") is not supported in this build.", null);

            return new LoaderData(mainClass, classPath.split(":"));
        } catch (Exception e) {
            throw error("Could not load library manifest.", e);
        }
    }

    private Class<?> loadMainClass(Path rootPath) {
        LoaderData data = getLoaderData(rootPath);

        try {
            URL[] urls = new URL[data.classPath.length];
            for(int i=0; i<data.classPath.length; i++) urls[i] = rootPath.resolve(data.classPath[i]).toUri().toURL();
            return new URLClassLoader(urls).loadClass(data.mainClass);
        } catch (Exception e) {
            throw error("Failed to load main class.", e);
        }
    }

    private void start(String[] args) {
        Path dir = getExecutableDirectory();
        System.setProperty("moe.lymia.princess.currentDirectory", dir.toUri().toString());
        Class<?> main = loadMainClass(dir.resolve("lib"));

        Method m;

        try {
            m = main.getMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            throw error("Main class does not declare a main method.", e);
        }

        try {
            m.invoke(null, (Object[]) args);
        } catch (IllegalAccessException e) {
            throw error("Main class is not accessible.", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Warning: Failed to set system Look and Feel.");
            e.printStackTrace();
        }

        new Loader().start(args);
    }
}
