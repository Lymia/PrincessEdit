/*
 * Copyright (c) 2017 Lymia Alusyia <lymia@lymiahugs.com>
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
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.GZIPInputStream;

public final class Loader implements Runnable {
    private static final Pack200.Unpacker unpacker = Pack200.newUnpacker();

    private File tempJarFile;
    private String mainClass;

    private URLClassLoader loader = null;

    private void loadPack200(InputStream in) throws IOException {
        tempJarFile = File.createTempFile("princess-edit-", ".jar");
        tempJarFile.deleteOnExit();

        JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(tempJarFile));
        unpacker.unpack(in, jarOut);
        jarOut.finish();

        JarInputStream jarIn = new JarInputStream(new FileInputStream(tempJarFile));
        mainClass = jarIn.getManifest().getMainAttributes().getValue("Main-Class");
        jarIn.close();

        System.out.println("Main-Class: "+mainClass);
    }

    private void startPackedProgram(String[] args) {
        Thread shutdownHook = new Thread(this);
        shutdownHook.setName("Loader shutdown hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try {
            loadPack200(new GZIPInputStream(new FileInputStream("PrincessEdit.pack.gz")));
        } catch (IOException e) {
            throw error("Could not parse pack200 contents.", e);
        }

        try {
            loader = new URLClassLoader(new URL[]{tempJarFile.toURI().toURL()}, ClassLoader.getSystemClassLoader());
        } catch (MalformedURLException e) {
            throw error("unexpected error", e);
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(mainClass, false, loader);
        } catch (ClassNotFoundException e) {
            throw error("Main-Class not found.", e);
        }

        Method m;
        try {
            m = clazz.getMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            throw error("main method not found.", e);
        }

        if(m == null) throw error("method is null", null);

        try {
            m.invoke(null, new Object[] { args });
        } catch (IllegalAccessException e) {
            throw error("Failed to call main method.", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Failed to call main method.", e.getCause());
        }
    }

    private static RuntimeException error(String error, Exception e) {
        JOptionPane.showMessageDialog(null, "Could not start PrincessEdit:\n"+error,
                                      "PrincessEdit", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
        return new RuntimeException(error, e);
    }

    @Override
    public void run() {
        if(loader != null) try {
            loader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        loader = null;

        if(tempJarFile != null) {
            tempJarFile.delete();
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Warning: Failed to set system Look and Feel.");
            e.printStackTrace();
        }

        new Loader().startPackedProgram(args);
    }
}
