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
public final class MathLib implements LuaJavaCallback
{
  // Each function in the library corresponds to an instance of
  // this class which is associated (the 'which' member) with an integer
  // which is unique within this class.  They are taken from the following
  // set.

  private static final int ABS = 1;
  private static final int ACOS = 2;
  private static final int ASIN = 3;
  private static final int ATAN2 = 4;
  private static final int ATAN = 5;
  private static final int CEIL = 6;
  private static final int COSH = 7;
  private static final int COS = 8;
  private static final int DEG = 9;
  private static final int EXP = 10;
  private static final int FLOOR = 11;
  private static final int FMOD = 12;
  private static final int FREXP = 13;
  private static final int LDEXP = 14;
  private static final int LOG = 15;
  private static final int LOG10 = 28;
  private static final int MAX = 16;
  private static final int MIN = 17;
  private static final int MODF = 18;
  private static final int POW = 19;
  private static final int RAD = 20;
  private static final int SINH = 23;
  private static final int SIN = 24;
  private static final int SQRT = 25;
  private static final int TANH = 26;
  private static final int TAN = 27;

  /**
   * Which library function this object represents.  This value should
   * be one of the "enums" defined in the class.
   */
  private final int which;

  /** Constructs instance, filling in the 'which' member. */
  private MathLib(int which)
  {
    this.which = which;
  }

  /**
   * Implements all of the functions in the Lua math library.  Do not
   * call directly.
   * @param L  the Lua state in which to execute.
   * @return number of returned parameters, as per convention.
   */
  public int luaFunction(Lua L)
  {
    switch (which)
    {
      case ABS:
        return abs(L);
      case ACOS:
        return acos(L);
      case ASIN:
        return asin(L);
      case ATAN2:
        return atan2(L);
      case ATAN:
        return atan(L);
      case CEIL:
        return ceil(L);
      case COSH:
        return cosh(L);
      case COS:
        return cos(L);
      case DEG:
        return deg(L);
      case EXP:
        return exp(L);
      case FLOOR:
        return floor(L);
      case FMOD:
        return fmod(L);
      case FREXP:
        return frexp(L);
      case LDEXP:
        return ldexp(L);
      case LOG:
        return log(L);
      case LOG10:
        return log10(L);
      case MAX:
        return max(L);
      case MIN:
        return min(L);
      case MODF:
        return modf(L);
      case POW:
        return pow(L);
      case RAD:
        return rad(L);
      case SINH:
        return sinh(L);
      case SIN:
        return sin(L);
      case SQRT:
        return sqrt(L);
      case TANH:
        return tanh(L);
      case TAN:
        return tan(L);
    }
    return 0;
  }

  /**
   * Opens the library into the given Lua state.  This registers
   * the symbols of the library in the global table.
   * @param L  The Lua state into which to open.
   */
  public static void open(Lua L)
  {
    LuaTable t = L.register("math");

    Random rng = new Random();

    r(L, "abs", ABS);
    r(L, "acos", ACOS);
    r(L, "asin", ASIN);
    r(L, "atan2", ATAN2);
    r(L, "atan", ATAN);
    r(L, "ceil", CEIL);
    r(L, "cosh", COSH);
    r(L, "cos", COS);
    r(L, "deg", DEG);
    r(L, "exp", EXP);
    r(L, "floor", FLOOR);
    r(L, "fmod", FMOD);
    r(L, "frexp", FREXP);
    r(L, "ldexp", LDEXP);
    r(L, "log", LOG);
    r(L, "log10", LOG10);
    r(L, "max", MAX);
    r(L, "min", MIN);
    r(L, "modf", MODF);
    r(L, "pow", POW);
    r(L, "rad", RAD);
    r(L, "random", MathLibRandom.RANDOM, rng);
    r(L, "randomseed", MathLibRandom.RANDOMSEED, rng);
    r(L, "sinh", SINH);
    r(L, "sin", SIN);
    r(L, "sqrt", SQRT);
    r(L, "tanh", TANH);
    r(L, "tan", TAN);

    L.setField(t, "pi", L.valueOfNumber(Math.PI));
    L.setField(t, "huge", L.valueOfNumber(Double.POSITIVE_INFINITY));
  }

  /** Register a function. */
  private static void r(Lua L, String name, int which, Random rng)
  {
    MathLibRandom f = new MathLibRandom(which, rng);
    L.setField(L.getGlobal("math"), name, f);
  }
  private static void r(Lua L, String name, int which)
  {
    MathLib f = new MathLib(which);
    L.setField(L.getGlobal("math"), name, f);
  }

  private static int abs(Lua L)
  {
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

  private static int ceil(Lua L)
  {
    L.pushNumber(Math.ceil(L.checkNumber(1)));
    return 1;
  }

  private static int cosh(Lua L)
  {
    L.pushNumber(Math.cosh(L.checkNumber(1)));
    return 1;
  }

  private static int cos(Lua L)
  {
    L.pushNumber(Math.cos(L.checkNumber(1)));
    return 1;
  }

  private static int deg(Lua L)
  {
    L.pushNumber(Math.toDegrees(L.checkNumber(1)));
    return 1;
  }

  private static int exp(Lua L)
  {
    L.pushNumber(Math.exp(L.checkNumber(1)));
    return 1;
  }

  private static int floor(Lua L)
  {
    L.pushNumber(Math.floor(L.checkNumber(1)));
    return 1;
  }

  private static int fmod(Lua L)
  {
    L.pushNumber(L.checkNumber(1) % L.checkNumber(2));
    return 1;
  }

  private static int frexp(Lua L)
  {
    double value = L.checkNumber(1);

    // code based on http://stackoverflow.com/a/3946294/1733590

    if (Double.isNaN(value) || value + value == value || Double.isInfinite(value)) {
      L.pushNumber(value);
      L.pushNumber(0);
      return 2;
    } else {
      long bits = Double.doubleToLongBits(value);

      boolean neg = bits < 0;
      int exponent = (int)((bits >> 52) & 0x7ffL);
      long mantissa = bits & 0xfffffffffffffL;

      if(exponent == 0) exponent++;
      else              mantissa = mantissa | (1L<<52);

      exponent -= 1075;
      double realMant = mantissa;

      while(realMant >= 1.0) {
        mantissa >>= 1;
        realMant /= 2;
        exponent++;
      }

      if(neg) realMant = -realMant;

      L.pushNumber(realMant);
      L.pushNumber(exponent);
      return 2;
    }
  }

  private static int ldexp(Lua L)
  {
    L.pushNumber(L.checkNumber(1) * Math.pow(2.0, L.checkNumber(2)));
    return 1;
  }

  private static int log(Lua L)
  {
    L.pushNumber(Math.log(L.checkNumber(1)));
    return 1;
  }

  private static int log10(Lua L)
  {
    L.pushNumber(Math.log10(L.checkNumber(1)));
    return 1;
  }

  private static int max(Lua L)
  {
    int n = L.getTop(); // number of arguments
    double dmax = L.checkNumber(1);
    for (int i=2; i<=n; ++i)
    {
      double d = L.checkNumber(i);
      dmax = Math.max(dmax, d);
    }
    L.pushNumber(dmax);
    return 1;
  }

  private static int min(Lua L)
  {
    int n = L.getTop(); // number of arguments
    double dmin = L.checkNumber(1);
    for (int i=2; i<=n; ++i)
    {
      double d = L.checkNumber(i);
      dmin = Math.min(dmin, d);
    }
    L.pushNumber(dmin);
    return 1;
  }

  private static int modf(Lua L)
  {
    double x = L.checkNumber(1);
    double fp = x % 1;
    double ip = x - fp;
    L.pushNumber(ip);
    L.pushNumber(fp);
    return 2;
  }

  private static int pow(Lua L)
  {
    L.pushNumber(Lua.iNumpow(L.checkNumber(1), L.checkNumber(2)));
    return 1;
  }

  private static int rad(Lua L)
  {
    L.pushNumber(Math.toRadians(L.checkNumber(1)));
    return 1;
  }

  private static int sinh(Lua L)
  {
    L.pushNumber(Math.sinh(L.checkNumber(1)));
    return 1;
  }

  private static int sin(Lua L)
  {
    L.pushNumber(Math.sin(L.checkNumber(1)));
    return 1;
  }

  private static int sqrt(Lua L)
  {
    L.pushNumber(Math.sqrt(L.checkNumber(1)));
    return 1;
  }

  private static int tanh(Lua L)
  {
    L.pushNumber(Math.tanh(L.checkNumber(1)));
    return 1;
  }

  private static int tan(Lua L)
  {
    L.pushNumber(Math.tan(L.checkNumber(1)));
    return 1;
  }
}

/**
 * Contains the random and randomseed functions
 */
class MathLibRandom implements LuaJavaCallback {
  static final int RANDOM = 21;
  static final int RANDOMSEED = 22;

  /**
   * Which library function this object represents.  This value should
   * be one of the "enums" defined in the class.
   */
  private final int which;

  /**
   * RNG pointer to use for this function
   */
  private final Random rng;

  MathLibRandom(int which, Random rng) {
    this.which = which;
    this.rng = rng;
  }

  @Override
  public int luaFunction(Lua L) {
    switch(which) {
      case RANDOM:
        return random(L);
      case RANDOMSEED:
        return randomseed(L);
    }
    return 0;
  }

  private int random(Lua L)
  {
    switch (L.getTop()) // check number of arguments
    {
      case 0:   // no arguments
        L.pushNumber(rng.nextDouble());
        break;

      case 1:   // only upper limit
        {
          int u = L.checkInt(1);
          L.argCheck(1<=u, 1, "interval is empty");
          L.pushNumber(rng.nextInt(u) + 1);
        }
        break;

      case 2:   // lower and upper limits
        {
          int l = L.checkInt(1);
          int u = L.checkInt(2);
          L.argCheck(l<=u, 2, "interval is empty");
          L.pushNumber(rng.nextInt(u) + l);
        }
        break;

      default:
        return L.error("wrong number of arguments");
    }
    return 1;
  }

  private int randomseed(Lua L)
  {
    rng.setSeed((long)L.checkNumber(1));
    return 0;
  }
}
