-- Copyright (c) 2017 Lymia Alusyia <lymia@lymiahugs.com>
--
-- Permission is hereby granted, free of charge, to any person obtaining a copy
-- of this software and associated documentation files (the "Software"), to deal
-- in the Software without restriction, including without limitation the rights
-- to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
-- copies of the Software, and to permit persons to whom the Software is
-- furnished to do so, subject to the following conditions:
--
-- The above copyright notice and this permission notice shall be included in
-- all copies or substantial portions of the Software.
--
-- THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
-- IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
-- FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
-- AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
-- LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
-- OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
-- THE SOFTWARE.

local _princess = ...
local core = _princess.core

local ipairs = ipairs

function core.lockAll(obj)
    obj._deleteProperty("_listProperties")
    obj._deleteProperty("_getProperty")
    obj._deleteProperty("_hasProperty")
    obj._lock()
end

function core.inheritProperty(obj, underlying, ...)
    for _, name in ipairs({...}) do
        local get, set = underlying._getProperty(name)
        obj._property(name, function() return get() end, function(v) set(v) end)
    end
end

function core.inheritLockedProperty(obj, underlying, ...)
    for _, name in ipairs({...}) do
        obj._property(name, function() return underlying[name] end, function(v) underlying[name] = v end)
    end
end

function core.inheritUnboundMethod(obj, underlying, ...)
    for _, name in ipairs({...}) do
        local fn = underlying[name]
        obj._method(name, function(...) return fn(underlying, ...) end)
    end
end

function core.inheritMethod(obj, underlying, ...)
    for _, name in ipairs({...}) do
        local fn = underlying[name]
        obj._method(name, function(...) return fn(...) end)
    end
end