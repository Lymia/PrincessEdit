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

size  = {250, 350}
scale = 0.01 * inch

local black = {0, 0, 0}
local white = {255, 255, 255}

function layoutComponents(cardData)
    local text = cardData.text
    local blankCount = cardData.blankCount
    if not blankCount then
        local _, ret = text:gsub("_+", "")
        blankCount = ret
    end
    local isBlackCard = blankCount > 0

    local bgColor = isBlackCard and black or white
    local fgColor = isBlackCard and white or black

    local layout = component.BasicLayout(size)
    layout.addComponent(0, 0, component.Fill(size, bgColor))
    layout.addComponent(0, 0, component.Resource("cah/cah-logo.svg", size))
    layout.addComponent(59, 322.5, component.SimpleText("Cards Against Humanity", "FreeSans-Bold", 5.25, fgColor))

    if blankCount > 1 then
        layout.addComponent(200, 324, component.LeftAlign(component.SimpleText("PICK", "FreeSans-Bold", 10, fgColor)))
        layout.addComponent(215, 319, component.Circle(10, fgColor))
        layout.addComponent(215, 319    ,
            component.Center(component.SimpleText(tostring(blankCount), "FreeSans-Bold", 12, bgColor)))
    end
    if blankCount > 2 then
        layout.addComponent(200, 299, component.LeftAlign(component.SimpleText("DRAW", "FreeSans-Bold", 10, fgColor)))
        layout.addComponent(215, 294, component.Circle(10, fgColor))
        layout.addComponent(215, 294    ,
            component.Center(component.SimpleText(tostring(blankCount - 1), "FreeSans-Bold", 12, bgColor)))
    end

    local formatter = TextFormatter()
    formatter.font = "FreeSans-Bold"
    formatter.color = fgColor
    formatter.append(text)

    local textLayoutBounds = {25, 30, 225, 300}
    local textLayout = component.TextLayout(textLayoutBounds)

    textLayout.lineBreakSize = 1.25
    textLayout.startFontSize = 15
    textLayout.tryExtra = 1

    textLayout.areas.new("main", textLayoutBounds)
    textLayout.areas.main.text = formatter.getFormattedString()
    layout.addComponent(0, 0, textLayout)

    return component.Mask(component.Resource("cah/card-mask.svg", size), layout)
end
