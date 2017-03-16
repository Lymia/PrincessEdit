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

local size  = {250, 350}
local scale = 0.01 * inch

local black = {0, 0, 0}
local white = {255, 255, 255}

local font  = "FreeSans-Bold"

function cardForm()
    local textNode = ui.node.Input("text", ui.control.TextField)
    local overrideBlankCount = ui.node.Input("overrideBlankCount", ui.control.CheckBox("Override blank count"))
    local isDefault = overrideBlankCount.map(function(b) return not b end)

    local blankCount = ui.node.Input("blankCount", ui.control.TextField, isDefault, textNode.map(
        function(text)
            local _, ret = text:gsub("_+", "")
            return tostring(ret)
        end
    ))

    return {
        text = textNode,
        blankCount = blankCount.map(tonumber),
    }, ui.node.Grid {
        {ui.node.Label("Text"), x = 0, y = 0, anchor = ui.node.Grid.WEST},
        {textNode, x = 1, y = 0, xFill = true, xWeight = 1, yWeight = 1},

        {ui.node.Label("Blank Count"), x = 0, y = 1, anchor = ui.node.Grid.WEST},
        {overrideBlankCount, x = 1, y = 1, anchor = ui.node.Grid.WEST},
        {blankCount, x = 1, y = 2, xFill = true},
    }
end

local function indicatorLine(layout, x, y, header, count, fgColor, bgColor)
    layout.addComponent(x, y, component.LeftAlign(component.SimpleText(header, font, 10, fgColor)))
    layout.addComponent(x + 15, y - 5, component.Circle(10, fgColor))
    layout.addComponent(x + 15, y - 5, component.Center(component.SimpleText(tostring(count), font, 12, bgColor)))
end

function render(cardData)
    local text = cardData.text
    local blankCount = cardData.blankCount
    local isBlackCard = blankCount > 0

    local bgColor = isBlackCard and black or white
    local fgColor = isBlackCard and white or black

    local layout = component.BasicLayout(size)
    layout.addComponent(0, 0, component.Fill(size, bgColor))
    layout.addComponent(0, 0, component.Resource("cah/cah-logo.svg", size))
    layout.addComponent(59, 322.5, component.SimpleText("Cards Against Humanity", font, 5.25, fgColor))

    local textLayout = component.SimpleTextLayout({25, 30, 225, 300}, text, font, 15, fgColor)
    textLayout.lineBreakSize = 1.25
    layout.addComponent(0, 0, textLayout)

    if blankCount > 1 then
        indicatorLine(layout, 200, 324, "PICK", blankCount    , fgColor, bgColor)
    end
    if blankCount > 2 then
        indicatorLine(layout, 200, 299, "DRAW", blankCount - 1, fgColor, bgColor)
        textLayout.addExclusion({150, 275, 250, 350})
    end

    return {
        component = component.Mask(component.Resource("cah/card-mask.svg", size), layout),
        size = size, scale = scale,
    }
end