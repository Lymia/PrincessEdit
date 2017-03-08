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

local Template = _princess.Template

component.Template = Template

local function colorToHex(color)
    return string.format("#%02x%02x%02x", color[1], color[2], color[3])
end
local function wrapColor(component)
    local color
    local _, _ulColor_set =  component._getProperty("_ulColor")
    component._deleteProperty("_ulColor")
    component._property("color", function() return color end,
                                 function(newColor) _ulColor_set(colorToHex(newColor)); color = newColor end)
end

local function Mask(maskComponent, dataComponent, bounds)
    local mask = Template("_princess/templates/mask.xml", dataComponent.bounds or bounds)
    mask.target = dataComponent
    mask.mask   = maskComponent
    return mask
end
component.Mask = Mask

function component.Clip(clipPath, dataComponent, bounds)
    local mask = Template("_princess/templates/clip.xml", dataComponent.bounds or bounds)
    mask.target = dataComponent
    mask.clip   = clipPath
    return mask
end

local function Fill(bounds, color)
    local fill = Template("_princess/templates/fillRect.xml", bounds)
    wrapColor(fill)
    fill.color = color
    return fill
end
component.Fill = Fill

function component.FillShape(maskComponent, color)
    local fill = Fill(maskComponent.bounds, color)
    local mask = Mask(maskComponent, fill)
    mask._property("color", function() return fill.color end,
                            function(color) fill.color = color end)
    return mask
end

function component.Filter(filterPath, dataComponent, bounds)
    local filter = Template("_princess/templates/filter.xml", dataComponent.bounds or bounds)
    filter.filter = filterPath
    filter.target = dataComponent
    return filter
end

function component.Blend(dataComponent, blendMode, opacity, bounds)
    local filter = Template("_princess/templates/blend.xml", dataComponent.bounds or bounds)
    filter.blendMode = blendMode or "normal"
    filter.opacity   = opacity or 1
    filter.target    = dataComponent
    return filter
end

function component.Circle(radius, color)
    local circle = Template("_princess/templates/circle.xml", {0, 0})
    circle.allowOverflow = true
    local _ulRadius_get, _ulRadius_set = circle._getProperty("_ulRadius")
    circle._deleteProperty("_ulRadius")
    wrapColor(circle)

    circle._property("radius", function() return _ulRadius_get() end, function(r)
        _ulRadius_set(r)
        circle.bounds = {-radius, -radius, radius, radius}
    end)

    circle.radius = radius
    circle.color  = color

    return circle
end