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

local ipairs, error, trace, insert = ipairs, error, log.trace, table.insert
local systemGetExports, loadLuaExport, hasExport, gameId =
    core.systemGetExports, core.loadLuaExport, core.hasExport, core.gameId

local requirePath = {}
local systemRequire = {}
core.systemRequires = {}

do
    local loaded = {}
    local function loadRequire(type, system)
        for _, export in ipairs(systemGetExports(type, system)) do
            local path = export.path
            if not loaded[path] then
                loaded[path] = true
                trace("Found new Lua include path: "..path)
                insert(requirePath, path)
            end
        end
    end
    loadRequire("_princess/package-path", true)
    loadRequire(gameId.."/package-path")
    loadRequire("package-path")
end

local function requireFn(module)
    if systemRequire[module] then
        return systemRequire[module]
    end

    module = module:gsub("%.", "/")
    for _, path in ipairs(requirePath) do
        local fullPath = path..".lua"
        if hasExport(fullPath) then return loadLuaExport(fullPath) end
        local fullPath = path.."/init.lua"
        if hasExport(fullPath) then return loadLuaExport(fullPath) end
    end

    error("module '"..module.."' not found")
end

local requireCache = {}
function require(module)
    if not requireCache[module] then
        requireCache[module] = requireFn(module)
    end
    return requireCache[module]
end