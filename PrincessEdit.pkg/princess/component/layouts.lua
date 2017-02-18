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

function component.BaseSizedLayout(size)
    local layout = component.BaseLayout()
    layout._property("size", function() return size end,
                             function(newSize) size = newSize end)
    return layout
end

function component.BasicLayout(size)
    local layout = component.BaseSizedLayout(size)

    local components = {}
    layout.prerenderHandler = function()
        local prerender = {}
        for _, component in ipairs(components) do
            if not component.size then
                table.insert(prerender, component.component)
            end
        end
        return prerender
    end
    layout.layoutHandler = function(prerender)
        local ret = {}
        for _, component in ipairs(components) do
            local copy = {}
            for k, v in pairs(component) do copy[k] = v end
            copy.size = copy.size or prerender[component.component]
            table.insert(ret, copy)
        end
        return ret, layout.size
    end
    layout._method("addComponent", function(x, y, component, size)
        table.insert(components, {component = component, x = x, y = y, size = size or component.size})
    end)

    return layout
end