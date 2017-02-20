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

local function copyAttrs(target, v)
    if v.font         then target.font         = _princess.getFont(v.font) end
    if v.relativeSize then target.relativeSize = v.relativeSize            end
    if v.color        then target.color        = v.color                   end
    return target
end

function TextFormatter()
    local underlying = _princess.FormattedStringBuffer()
    local formatter  = _princess.Object()


    formatter._property("font", function() return _princess.getFontName(underlying.font) end,
                                function(v) underlying.font = _princess.getFont(v) end)
    for _, name in ipairs({"relativeSize", "color"}) do
        formatter._property(name, function() return underlying[name] end, function(v) underlying[name] = v end)
    end
    formatter._property("attributes", function()
        return {font         = _princess.getFontName(underlying.font),
                relativeSize = underlying.relativeSize,
                color        = underlying.color}
    end, function(v)
        copyAttrs(underlying, v)
    end)

    for _, name in ipairs({"getFormattedString", "paragraphBreak", "lineBreak"}) do
        local fn = underlying[name]
        formatter._method(name, function(...) return fn(underlying, ...) end)
    end
    formatter._method("append", function(str, attrs)
        if attrs then
            if attrs.font then attrs = copyAttrs({}, attrs) end
            return underlying:appendWithAttributes(str, attrs)
        else
            return underlying:append(str)
        end
    end)

    formatter._lock()

    return formatter
end

function component.SimpleText(string, font, size, color)
    font = _princess.getFont(font)
    -- TODO: Override font property in SimpleText
    return _princess.SimpleText(string, font, size, color)
end

component.SimpleFormattedText = _princess.SimpleFormattedText
