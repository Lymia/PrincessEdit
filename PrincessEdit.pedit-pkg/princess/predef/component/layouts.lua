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

local pairs, ipairs = pairs, ipairs
local table_insert = table.insert
local BaseLayout, BaseSizedLayout = _princess.BaseLayout, _princess.BaseSizedLayout

component.BaseLayout = BaseLayout
component.BasicSizedLayout = BaseSizedLayout

function component.BasicLayout(size)
    local layout = BaseSizedLayout(size)

    local components = {}
    layout.layoutHandler = function()
        return {
            components = components,
            bounds     = layout.bounds,
        }
    end
    layout._method("addComponent", function(x, y, component, size)
        table_insert(components, {component = component, x = x, y = y, size = size})
    end)
    layout._method("addComponentInBounds", function(component, bounds)
        table_insert(components, {component = component, bounds = bounds})
    end)

    return layout
end

local function SingleComponentLayout(component)
    local layout = BaseLayout()

    local boundsHandler = function() error("no bounds function given") end
    layout._property("boundsHandler", function() return boundsHandler end, function(f) boundsHandler = f end)

    layout.prerenderHandler = function()
        return { component }
    end
    layout.layoutHandler = function(prerender)
        local bounds = boundsHandler(prerender[component].bounds, prerender[component].size)
        return {
            components = { { component = component, bounds = bounds } },
            bounds     = bounds,
        }
    end
    layout._property("target", function() return component end, function(c) component = c end)

    return layout
end

function component.LeftAlign(component)
    local layout = SingleComponentLayout(component)
    function layout.boundsHandler(bounds, size)
        return { bounds[1] - bounds[3], bounds[2], 0, bounds[4] }
    end
    return layout
end

function component.Center(component)
    local layout = SingleComponentLayout(component)
    function layout.boundsHandler(bounds, size)
        return { -(size[1]/2), -(size[2]/2), size[1]/2, size[2]/2 }
    end
    return layout
end