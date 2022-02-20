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

local ipairs, pairs, error, type, _G = ipairs, pairs, error, type, _G
local log, TRACE, where = core.log, core.LogLevel.TRACE, core.where

-----------------------------------
-- Remove non-whitelisted functions

local function trace(str)
    return log(TRACE, where(1), function() return str end)
end

local function set(t)
    local n = {}
    for _, v in ipairs(t) do n[v] = true end
    return n
end
core.set = set -- TODO: Maybe define this somewhere nicer?

local absoluteWhitelist = set {
    "assert", "error", "getmetatable", "next", "pairs", "pcall", "rawequal", "rawget", "rawset", "select",
    "setmetatable", "tonumber", "tostring", "type", "unpack", "_VERSION", "xpcall", "ipairs",
    -- tables
    "_G",
}
local tableWhitelist = {
    coroutine = set { "create", "resume", "running", "status", "wrap", "yield" },
    string = set {
        "byte", "char", "dump", "find", "format", "gmatch", "gfind", "gsub", "len", "lower", "match", "rep",
        "reverse", "sub", "upper"
    },
    table = set { "concat", "insert", "maxn", "remove", "sort" },
    math = set {
        "abs", "acos", "asin", "atan", "atan2", "ceil", "cos", "cosh", "deg", "exp", "floor", "fmod", "frexp",
        "huge", "ldexp", "log", "log10", "max", "min", "modf", "pi", "pow", "rad", "random", "randomseed",
        "sin", "sinh", "sqrt", "tan", "tanh"
    },
    os = set { "clock", "date", "difftime", "time" },
}

local function cleanTable(name)
    local table = _G[name]
    if not table then
        error("Expected "..name.." in environment, but value not found.")
    end
    local expected = tableWhitelist[name]
    for k, _ in pairs(expected) do
        if not table[k] then
            error("Expected "..name.."."..k.." in environment, but value not found.")
        end
    end
    for k, _ in pairs(table) do
        if not expected[k] then
            trace("Removing "..name.."."..k.." from environment.")
            table[k] = nil
        end
    end
end
for k, _ in pairs(absoluteWhitelist) do
    if not _G[k] then
        error("Expected "..k.." in environment, but value not found.")
    end
end
for k, _ in pairs(_G) do
    if absoluteWhitelist[k] then
        -- do nothing
    elseif tableWhitelist[k] then
        cleanTable(k)
    else
        trace("Removing "..k.." from environment.")
        _G[k] = nil
    end
end

--------------------
-- Metatable wrapper

local sys_setmetatable, sys_getmetatable = setmetatable, getmetatable
core.setmetatable = sys_setmetatable
core.getmetatable = sys_getmetatable

local function checkMetatableType(obj)
    local t = type(obj)
    if not (t == "table" or t == "userdata") then
        error("cannot modify metatables for type "..t)
    end
end
function setmetatable(obj, mt)
    checkMetatableType(obj)
    return sys_setmetatable(obj, mt)
end
function getmetatable(obj)
    checkMetatableType(obj)
    return sys_getmetatable(obj)
end