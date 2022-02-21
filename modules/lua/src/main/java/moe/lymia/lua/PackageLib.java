/*
 * Copyright (c) 2006 Nokia Corporation and/or its subsidiary(-ies).
 * Copyright (c) 2017-2022 Lymia Alusyia <lymia@lymiahugs.com>
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject
 * to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 * ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package moe.lymia.lua;

import java.io.IOException;
import java.io.InputStream;

/**
 * Contains Lua's package library.
 * The library
 * can be opened using the {@link #open} method.
 */
public final class PackageLib {
    /**
     * Opens the library into the given Lua state.  This registers
     * the symbols of the library in the global table.
     *
     * @param L The Lua state into which to open.
     */
    public static void open(Lua L) {
        LuaTable packageTable = L.register("package");

        r(L, "module", L2 -> module(L2, packageTable));
        r(L, "require", L2 -> require(L2, packageTable));

        r(L, "seeall", PackageLib::seeall);

        L.setField(packageTable, "loaders", L.newTable());
        p(L, packageTable, L2 -> loaderPreload(L2, packageTable));
        p(L, packageTable, L2 -> loaderLua(L2, packageTable));
        setpath(L, packageTable, "path", PATH_DEFAULT);        // set field 'path'

        // set field 'loaded'
        L.findTable(L.getRegistry(), Lua.LOADED, 1);
        L.setField(packageTable, "loaded", L.value(-1));
        L.pop(1);
        L.setField(packageTable, "preload", L.newTable());
    }

    /**
     * Register a function.
     */
    private static void r(Lua L, String name, LuaJavaCallback fn) {
        L.setField(L.getGlobal("package"), name, fn);
    }

    /**
     * Register a loader in package.loaders.
     */
    private static void p(Lua L, LuaTable t, LuaJavaCallback fn) {
        Object loaders = L.getField(t, "loaders");
        L.rawSetI(loaders, L.objLen(loaders) + 1, fn);
    }

    private static final String DIRSEP = "/";
    private static final char PATHSEP = ';';
    private static final String PATH_MARK = "?";
    private static final String PATH_DEFAULT = "?.lua;?/init.lua";

    private static final Object SENTINEL = new Object();

    /**
     * Implements the preload loader.  This is conventionally stored
     * first in the package.loaders table.
     */
    private static int loaderPreload(Lua L, LuaTable me) {
        String name = L.checkString(1);
        Object preload = L.getField(me, "preload");
        if (!L.isTable(preload))
            L.error("'package.preload' must be a table");
        Object loader = L.getField(preload, name);
        if (L.isNil(loader))        // not found?
            L.pushString("\n\tno field package.preload['" + name + "']");
        L.push(loader);
        return 1;
    }

    /**
     * Implements the lua loader.  This is conventionally stored second in
     * the package.loaders table.
     */
    private static int loaderLua(Lua L, LuaTable me) {
        String name = L.checkString(1);
        String filename = findfile(L, me, name, "path");
        if (filename == null)
            return 1; // library not found in this path
        if (L.loadFile(filename) != 0)
            loaderror(L, filename);
        return 1;   // library loaded successfully
    }

    /**
     * Implements module.
     */
    private static int module(Lua L, LuaTable me) {
        String modname = L.checkString(1);
        Object loaded = L.getField(me, "loaded");
        Object module = L.getField(loaded, modname);
        if (!L.isTable(module))     // not found?
        {
            // try global variable (and create one if it does not exist)
            if (L.findTable(L.getGlobals(), modname, 1) != null)
                return L.error("name conflict for module '" + modname + "'");
            module = L.value(-1);
            L.pop(1);
            // package.loaded = new table
            L.setField(loaded, modname, module);
        }
        // check whether table already has a _NAME field
        if (L.isNil(L.getField(module, "_NAME"))) {
            modinit(L, module, modname);
        }
        setfenv(L, module);
        dooptions(L, module, L.getTop());
        return 0;
    }

    /**
     * Implements require.
     */
    private static int require(Lua L, LuaTable me) {
        String name = L.checkString(1);
        L.setTop(1);
        // PUC-Rio's use of lua_getfield(L, LUA_REGISTRYINDEX, "_LOADED");
        // (package.loaded is kept in the registry in PUC-Rio) is translated
        // into this:
        Object loaded = L.getField(me, "loaded");
        Object module = L.getField(loaded, name);
        if (L.toBoolean(module))    // is it there?
        {
            if (module == SENTINEL)   // check loops
                L.error("loop or previous error loading module '" + name + "'");
            L.push(module);
            return 1;
        }
        // else must load it; iterate over available loaders.
        Object loaders = L.getField(me, "loaders");
        if (!L.isTable(loaders))
            L.error("'package.loaders' must be a table");
        L.pushString("");   // error message accumulator
        for (int i = 1; ; ++i) {
            Object loader = L.rawGetI(loaders, i);    // get a loader
            if (L.isNil(loader))
                L.error("module '" + name + "' not found:" +
                        L.toString(L.value(-1)));
            L.push(loader);
            L.pushString(name);
            L.call(1, 1);     // call it
            if (L.isFunction(L.value(-1)))    // did it find module?
                break;  // module loaded successfully
            else if (L.isString(L.value(-1))) // loader returned error message?
                L.concat(2);    // accumulate it
            else
                L.pop(1);
        }
        L.setField(loaded, name, SENTINEL); // package.loaded[name] = sentinel
        L.pushString(name); // pass name as argument to module
        L.call(1, 1);       // run loaded module
        if (!L.isNil(L.value(-1)))  // non-nil return?
        {
            // package.loaded[name] = returned value
            L.setField(loaded, name, L.value(-1));
        }
        module = L.getField(loaded, name);
        if (module == SENTINEL)  // module did not set a value?
        {
            module = L.valueOfBoolean(true);  // use true as result
            L.setField(loaded, name, module); // package.loaded[name] = true
        }
        L.push(module);
        return 1;
    }

    /**
     * Implements package.seeall.
     */
    private static int seeall(Lua L) {
        L.checkType(1, Lua.TTABLE);
        LuaTable mt = L.getMetatable(L.value(1));
        if (mt == null) {
            mt = L.createTable(0, 1);
            L.setMetatable(L.value(1), mt);
        }
        L.setField(mt, "__index", L.getGlobals());
        return 0;
    }

    /**
     * Helper for module.  <var>module</var> parameter replaces PUC-Rio
     * use of passing it on the stack.
     */
    private static void setfenv(Lua L, Object module) {
        Debug ar = L.getStack(1);
        L.getInfo("f", ar);
        L.setFenv(L.value(-1), module);
        L.pop(1);
    }

    /**
     * Helper for module.  <var>module</var> parameter replaces PUC-Rio
     * use of passing it on the stack.
     */
    private static void dooptions(Lua L, Object module, int n) {
        for (int i = 2; i <= n; ++i) {
            L.pushValue(i);   // get option (a function)
            L.push(module);
            L.call(1, 0);
        }
    }

    /**
     * Helper for module.  <var>module</var> parameter replaces PUC-Rio
     * use of passing it on the stack.
     */
    private static void modinit(Lua L, Object module, String modname) {
        L.setField(module, "_M", module);   // module._M = module
        L.setField(module, "_NAME", modname);
        int dot = modname.lastIndexOf('.'); // look for last dot in module name
        // Surprisingly, ++dot works when '.' was found and when it wasn't.
        ++dot;
        // set _PACKAGE as package name (full module name minus last part)
        L.setField(module, "_PACKAGE", modname.substring(0, dot));
    }

    private static void loaderror(Lua L, String filename) {
        L.error("error loading module '" + L.toString(L.value(1)) +
                "' from file '" + filename + "':\n\t" +
                L.toString(L.value(-1)));
    }

    private static boolean readable(String filename) {
        InputStream f = PackageLib.class.getResourceAsStream(filename);
        if (f == null)
            return false;
        try {
            f.close();
        } catch (IOException ignored) {
        }
        return true;
    }

    private static String pushnexttemplate(Lua L, String path) {
        int i = 0;
        // skip seperators
        while (i < path.length() && path.charAt(i) == PATHSEP)
            ++i;
        if (i == path.length())
            return null;      // no more templates
        int l = path.indexOf(PATHSEP, i);
        if (l < 0)
            l = path.length();
        L.pushString(path.substring(i, l)); // template
        return path.substring(l);
    }

    private static String findfile(Lua L, LuaTable me, String name, String pname) {
        name = gsub(name, ".", DIRSEP);
        String path = L.toString(L.getField(me, pname));
        if (path == null)
            L.error("'package." + pname + "' must be a string");
        L.pushString("");   // error accumulator
        while (true) {
            path = pushnexttemplate(L, path);
            if (path == null)
                break;
            String filename = gsub(L.toString(L.value(-1)), PATH_MARK, name);
            if (readable(filename))   // does file exist and is readable?
                return filename;        // return that file name
            L.pop(1); // remove path template
            L.pushString("\n\tno file '" + filename + "'");
            L.concat(2);
        }
        return null;        // not found
    }

    /**
     * Almost equivalent to luaL_gsub.
     */
    private static String gsub(String s, String p, String r) {
        StringBuilder b = new StringBuilder();
        // instead of incrementing the char *s, we use the index i
        int i = 0;
        int l = p.length();

        while (true) {
            int wild = s.indexOf(p, i);
            if (wild < 0)
                break;
            b.append(s.substring(i, wild));   // add prefix
            b.append(r);      // add replacement in place of pattern
            i = wild + l;     // continue after 'p'
        }
        b.append(s.substring(i));
        return b.toString();
    }

    private static void setpath(Lua L,
                                LuaTable t,
                                String fieldname,
                                String def) {
        // :todo: consider implementing a user-specified path via
        // javax.microedition.midlet.MIDlet.getAppProperty or similar.
        // Currently we just use a default path defined by Jill.
        L.setField(t, fieldname, def);
    }
}
