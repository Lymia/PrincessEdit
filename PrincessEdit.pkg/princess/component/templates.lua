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

function component.Mask(maskComponent, dataComponent)
    local mask = component.Template("princess/component/mask.xml", dataComponent.size)
    mask.target = dataComponent
    mask.mask   = maskComponent
    return mask
end

function component.Clip(clipPath, dataComponent)
    local mask = component.Template("princess/component/clip.xml", dataComponent.size)
    mask.target = dataComponent
    mask.clip   = clipPath
    return mask
end

function component.Fill(size, color)
    local fill = component.Template("princess/component/fillRect.xml", size)
    function fill._prop.set_color(color)
        fill._ulColor = util.colorToHex(color)
    end
    function fill._prop.get_color()
        return fill._ulColor
    end
    fill.color = color
    return fill
end

function component.FillShape(maskComponent, color)
    local fill = component.Fill(maskComponent.size, color)
    local mask = component.Mask(maskComponent, fill)
    function mask._prop.set_color(color)
        fill.color = color
    end
    function mask._prop.get_color()
        return fill.color
    end
    return mask
end