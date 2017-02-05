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

function component.mask(maskComponent, dataComponent)
    local mask = component.fromTemplate("princess/component/mask.xml", dataComponent.size)
    mask.target = dataComponent
    mask.mask   = maskComponent
    return mask
end

function component.clip(maskComponent, dataComponent)
    local mask = component.fromTemplate("princess/component/clip.xml", dataComponent.size)
    mask.target = dataComponent
    mask.mask   = maskComponent
    return mask
end

function component.fill(size, color)
    local mask = component.fromTemplate("princess/component/fillRect.xml", size)
    mask.color = color
    return mask
end

function component.fillShape(maskComponent, color)
    local fill = component.fill(maskComponent.size, color)
    local clip = component.clip(maskComponent, fill)
    function clip._prop.set_color(color)
        fill.color = color
    end
    function clip._prop.get_color()
        return fill.color
    end
    return clip
end