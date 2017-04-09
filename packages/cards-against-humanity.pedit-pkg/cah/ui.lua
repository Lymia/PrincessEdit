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

function cardForm()
    local textNode = ui.node.Input("text", ui.control.TextField)
    local overrideBlankCount = ui.node.Input("overrideBlankCount", ui.control.CheckBox("$cah.overrideBlankCount"))
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
        {ui.node.Label("$cah.text"), x = 0, y = 0, xAlign = ui.node.Grid.BEGINNING},
        {textNode, x = 1, y = 0, xFill = true, xExpand = true},

        {ui.node.Label("$cah.blankCount"), x = 0, y = 1, xAlign = ui.node.Grid.BEGINNING},
        {overrideBlankCount, x = 1, y = 1, xAlign = ui.node.Grid.BEGINNING},
        {blankCount, x = 1, y = 2, xFill = true, xExpand = true},
    }
end

function cardColumns()
    local text = ui.Column("$cah.text", 400, function(card) return card.text end)
    local cardType = ui.Column("$cah.cardType", 75,
        function(card) return i18n((card.blankCount > 0) and "cah.black" or "cah.white") end)
    return { text, cardType }, { text, cardType }
end