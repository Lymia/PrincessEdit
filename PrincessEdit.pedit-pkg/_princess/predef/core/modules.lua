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

local setmetatable, pairs, ipairs = setmetatable, pairs, ipairs
local getExports, loadLuaExport, loadINIExport = core.getExports, core.loadLuaExport, core.loadINIExport

exports = {}

exports.getExportList = core.getExportList
exports.loadLua = loadLuaExport

local function wrapSection(section)
    -- We wrap the methods in a metadata so they don't show up in pairs() iteration
    local mt = { __index = {} }
    function mt.__index.getSingle(k)
        local v = section[k]
        if not v then return nil end
        if #v > 1 then return nil end
        return v[1]
    end
    function mt.__index.getMulti(k)
        return section[k] or {}
    end
    function mt.__index.checkFlag(k)
        return not not section[k]
    end

    setmetatable(section, mt)
    return mt.__index
end

local function loadINI(path)
    local ini = loadINIExport(path)
    for _, v in pairs(ini) do
        wrapSection(v)
    end
    return ini
end
exports.loadINI = loadINI

local function getExportsFn(allowSystem)
    return function(type, system)
        local list = getExports(type, allowSystem and system)

        for _, export in ipairs(list) do
            local path = export.path

            local mt = wrapSection(export.metadata)
            for k, v in pairs(mt) do
                export[k] = v
            end

            function export.loadLua()
                return loadLuaExport(path)
            end
            function export.loadINI()
                return loadINI(path)
            end
        end

        return list
    end
end

core.systemGetExports = getExportsFn(true)
exports.getExports = getExportsFn(false)
