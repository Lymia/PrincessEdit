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

viewTypeName = "$cah.expansion"

function viewForm()
    local expansionName = ui.node.Input("expansionName", ui.control.TextField)
    local expansionCode = ui.node.Input("expansionCode", ui.control.TextField)
    local cardGameName = ui.node.Input("cardGameName", ui.control.TextField, "Cards Against Humanity")

    return {
        expansion = {
            name = expansionName,
            code = expansionCode,
            gameName = cardGameName
        }
    }, ui.node.Labeled {
        { "$cah.expansionName", expansionName },
        { "$cah.expansionCode", expansionCode },
        { "$cah.cardGameName", cardGameName }
    }
end

function viewName(root)
    local name = root.expansion.name
    if #name == 0 then
        return i18n("cah.unnamed")
    else
        return name
    end
end

function cardForm()
    local text = ui.node.Input("text", ui.control.TextField)
    local overrideBlankCount = ui.node.Input("overrideBlankCount", ui.control.CheckBox("$cah.overrideBlankCount"))
    local isDefault = overrideBlankCount.map(function(b) return not b end)

    local blankCount = ui.node.Input("blankCount", ui.control.TextField, isDefault, text.map(
        function(text)
            local _, ret = text:gsub("_+", "")
            return tostring(ret)
        end
    ))
    local intBlankCount = blankCount.map(tonumber)

    return {
        text = text,
        blankCount = intBlankCount,
        isBlack = intBlankCount.map(function(x) return x > 0 end)
    }, ui.node.Labeled {
        {"$cah.text", text },
        {"$cah.blankCount", {
            {overrideBlankCount, xAlign = ui.node.Grid.BEGINNING},
            {blankCount, xFill = true, xExpand = true}
        }}
    }
end

function cardColumns()
    local text = ui.Column("$cah.text", 400, function(card) return card.text end, nil, true)
    local cardType = ui.Column("$cah.cardType", 75,
        function(card) return i18n(card.isBlack and "cah.black" or "cah.white") end,
        function(a, b) return (a.isBlack and 1 or 0) - (b.isBlack and 1 or 0) end, true)
    return { text, cardType }
end