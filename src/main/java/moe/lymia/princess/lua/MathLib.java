/*  $Header: //info.ravenbrook.com/project/jili/version/1.1/code/mnj/lua/MathLib.java#1 $
 * Copyright (c) 2006 Nokia Corporation and/or its subsidiary(-ies).
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
 */

package moe.lymia.princess.lua;

import java.util.Random;

/**
 * Contains Lua's math library.
 * The library can be opened using the {@link #open} method.
 * Because this library is implemented on top of CLDC 1.1 it is not as
 * complete as the PUC-Rio math library.  Trigononmetric inverses
 * (EG <code>acos</code>) and hyperbolic trigonometric functions (EG
 * <code>cosh</code>) are not provided.
 */
public final class MathLib {
    /**
     * Opens the library into the given Lua state.  This registers
     * the symbols of the library in the global table.
     *
     * @param L The Lua state into which to open.
     */
    public static void open(Lua L) {
        LuaTable t = L.register("math");

        Random rng = new Random();

        r(L, "abs", MathLib::abs);
        r(L, "acos", MathLib::acos);
        r(L, "asin", MathLib::asin);
        r(L, "atan2", MathLib::atan2);
        r(L, "atan", MathLib::atan);
        r(L, "ceil", MathLib::ceil);
        r(L, "cosh", MathLib::cosh);
        r(L, "cos", MathLib::cos);
        r(L, "deg", MathLib::deg);
        r(L, "exp", MathLib::exp);
        r(L, "floor", MathLib::floor);
        r(L, "fmod", MathLib::fmod);
        r(L, "frexp", MathLib::frexp);
        r(L, "ldexp", MathLib::ldexp);
        r(L, "log", MathLib::log);
        r(L, "log10", MathLib::log10);
        r(L, "max", MathLib::max);
        r(L, "min", MathLib::min);
        r(L, "modf", MathLib::modf);
        r(L, "pow", MathLib::pow);
        r(L, "rad", MathLib::rad);
        r(L, "random", L2 -> random(L2, rng));
        r(L, "randomseed", L2 -> randomseed(L2, rng));
        r(L, "sinh", MathLib::sinh);
        r(L, "sin", MathLib::sin);
        r(L, "sqrt", MathLib::sqrt);
        r(L, "tanh", MathLib::tanh);
        r(L, "tan", MathLib::tan);

        L.setField(t, "pi", L.valueOfNumber(Math.PI));
        L.setField(t, "huge", L.valueOfNumber(Double.POSITIVE_INFINITY));
    }

    private static void r(Lua L, String name, LuaJavaCallback fn) {
        L.setField(L.getGlobal("math"), name, fn);
    }

    private static int abs(Lua L) {
        L.pushNumber(Math.abs(L.checkNumber(1)));
        return 1;
    }

    private static int acos(Lua L) {
        L.pushNumber(Math.acos(L.checkNumber(1)));
        return 1;
    }

    private static int asin(Lua L) {
        L.pushNumber(Math.asin(L.checkNumber(1)));
        return 1;
    }

    private static int atan2(Lua L) {
        L.pushNumber(Math.atan2(L.checkNumber(1), L.checkNumber(2)));
        return 1;
    }

    private static int atan(Lua L) {
        L.pushNumber(Math.atan(L.checkNumber(1)));
        return 1;
    }

    private static int ceil(Lua L) {
        L.pushNumber(Math.ceil(L.checkNumber(1)));
        return 1;
    }

    private static int cosh(Lua L) {
        L.pushNumber(Math.cosh(L.checkNumber(1)));
        return 1;
    }

    private static int cos(Lua L) {
        L.pushNumber(Math.cos(L.checkNumber(1)));
        return 1;
    }

    private static int deg(Lua L) {
        L.pushNumber(Math.toDegrees(L.checkNumber(1)));
        return 1;
    }

    private static int exp(Lua L) {
        L.pushNumber(Math.exp(L.checkNumber(1)));
        return 1;
    }

    private static int floor(Lua L) {
        L.pushNumber(Math.floor(L.checkNumber(1)));
        return 1;
    }

    private static int fmod(Lua L) {
        L.pushNumber(L.checkNumber(1) % L.checkNumber(2));
        return 1;
    }

    private static int frexp(Lua L) {
        double value = L.checkNumber(1);

        // code based on http://stackoverflow.com/a/3946294/1733590

        if (Double.isNaN(value) || value + value == value || Double.isInfinite(value)) {
            L.pushNumber(value);
            L.pushNumber(0);
            return 2;
        } else {
            long bits = Double.doubleToLongBits(value);

            boolean neg = bits < 0;
            int exponent = (int) ((bits >> 52) & 0x7ffL);
            long mantissa = bits & 0xfffffffffffffL;

            if (exponent == 0) exponent++;
            else mantissa = mantissa | (1L << 52);

            exponent -= 1075;
            double realMant = mantissa;

            while (realMant >= 1.0) {
                mantissa >>= 1;
                realMant /= 2;
                exponent++;
            }

            if (neg) realMant = -realMant;

            L.pushNumber(realMant);
            L.pushNumber(exponent);
            return 2;
        }
    }

    private static int ldexp(Lua L) {
        L.pushNumber(L.checkNumber(1) * Math.pow(2.0, L.checkNumber(2)));
        return 1;
    }

    private static int log(Lua L) {
        L.pushNumber(Math.log(L.checkNumber(1)));
        return 1;
    }

    private static int log10(Lua L) {
        L.pushNumber(Math.log10(L.checkNumber(1)));
        return 1;
    }

    private static int max(Lua L) {
        int n = L.getTop(); // number of arguments
        double dmax = L.checkNumber(1);
        for (int i = 2; i <= n; ++i) {
            double d = L.checkNumber(i);
            dmax = Math.max(dmax, d);
        }
        L.pushNumber(dmax);
        return 1;
    }

    private static int min(Lua L) {
        int n = L.getTop(); // number of arguments
        double dmin = L.checkNumber(1);
        for (int i = 2; i <= n; ++i) {
            double d = L.checkNumber(i);
            dmin = Math.min(dmin, d);
        }
        L.pushNumber(dmin);
        return 1;
    }

    private static int modf(Lua L) {
        double x = L.checkNumber(1);
        double fp = x % 1;
        double ip = x - fp;
        L.pushNumber(ip);
        L.pushNumber(fp);
        return 2;
    }

    private static int pow(Lua L) {
        L.pushNumber(Math.pow(L.checkNumber(1), L.checkNumber(2)));
        return 1;
    }

    private static int rad(Lua L) {
        L.pushNumber(Math.toRadians(L.checkNumber(1)));
        return 1;
    }

    private static int sinh(Lua L) {
        L.pushNumber(Math.sinh(L.checkNumber(1)));
        return 1;
    }

    private static int sin(Lua L) {
        L.pushNumber(Math.sin(L.checkNumber(1)));
        return 1;
    }

    private static int sqrt(Lua L) {
        L.pushNumber(Math.sqrt(L.checkNumber(1)));
        return 1;
    }

    private static int tanh(Lua L) {
        L.pushNumber(Math.tanh(L.checkNumber(1)));
        return 1;
    }

    private static int tan(Lua L) {
        L.pushNumber(Math.tan(L.checkNumber(1)));
        return 1;
    }

    private static int random(Lua L, Random rng) {
        switch (L.getTop()) // check number of arguments
        {
            case 0:   // no arguments
                L.pushNumber(rng.nextDouble());
                break;

            case 1:   // only upper limit
            {
                int u = L.checkInt(1);
                L.argCheck(1 <= u, 1, "interval is empty");
                L.pushNumber(rng.nextInt(u) + 1);
            }
            break;

            case 2:   // lower and upper limits
            {
                int l = L.checkInt(1);
                int u = L.checkInt(2);
                L.argCheck(l <= u, 2, "interval is empty");
                L.pushNumber(rng.nextInt(u) + l);
            }
            break;

            default:
                return L.error("wrong number of arguments");
        }
        return 1;
    }

    private static int randomseed(Lua L, Random rng) {
        rng.setSeed((long) L.checkNumber(1));
        return 0;
    }
}
