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

local pairs, ipairs, type = pairs, ipairs, type
local table_insert = table.insert
local Grid = ui.node.Grid

function ui.node.Labeled(tbl)
    local components = {}
    components.xSpacing = 10

    local i = 0
    for _, row in ipairs(tbl) do
        local label = row.label or row.text or row[1]
        if type(label) == "string" then label = ui.node.Label(label) end
        table_insert(components, { label, x = 0, y = i, xAlign = ui.node.Grid.BEGINNING })

        local componentList = row.components or row.component or row[2]
        if type(componentList) ~= "table" then componentList = {componentList} end

        for _, component in ipairs(componentList) do
            if type(component) ~= "table" then component = {component} end

            local componentTable = { component.component or component[1], xFill = true, xExpand = true }
            for k, v in pairs(row) do
                if type(k) == "string" then componentTable[k] = v end
            end
            for k, v in pairs(component) do
                if type(k) == "string" then componentTable[k] = v end
            end
            componentTable.x = 1
            componentTable.y = i
            table_insert(components, componentTable)

            i = i + 1
        end
    end
    for k, v in pairs(tbl) do
        if type(k) == "string" then components[k] = v end
    end

    return Grid(components)
end