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

local Template = _princess.Template

component.Template = Template

local function colorToHex(color)
    return string.format("#%02x%02x%02x", color[1], color[2], color[3])
end

local function Mask(maskComponent, dataComponent)
    local mask = Template("princess/component/mask.xml", dataComponent.bounds)
    mask.target = dataComponent
    mask.mask   = maskComponent
    return mask
end
component.Mask = Mask

function component.Clip(clipPath, dataComponent)
    local mask = Template("princess/component/clip.xml", dataComponent.bounds)
    mask.target = dataComponent
    mask.clip   = clipPath
    return mask
end

local function Fill(bounds, color)
    local fill = Template("princess/component/fillRect.xml", bounds)
    fill._property("color", function() return color end,
                            function(newColor) fill._ulColor = colorToHex(newColor); color = newColor end)
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