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

function require(s)
    return module.load(s:gsub("%.", "/")..".lua")
end

function module.getExports(type)
    local list = module.getExportsRaw(type)

    function list.load()
        module.load(list.path)
    end

    -- We wrap the methods in a metadata so they don't show up in pairs() iteration
    local metadata = list.metadata
    local mt = { __index = {} }
    function mt.__index.getSingle(k)
        local v = metadata[k]
        if not v then return nil end
        if #v > 1 then return nil end
        return v[1]
    end
    function mt.__index.getMulti(k)
        return metadata[k] or {}
    end
    setmetatable(metadata, mt)

    return list
end

