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

-- TODO: Add explicit nil checks

local _princess = ...
local core, render = _princess.core, _princess.render

local ipairs = ipairs
local getFont, getFontName, lockAll =
    render.getFont, render.getFontName, core.lockAll
local inheritProperty, inheritMethod, inheritUnboundMethod, inheritLockedProperty =
    core.inheritProperty, core.inheritMethod, core.inheritUnboundMethod, core.inheritLockedProperty
local FormattedStringBuffer, Object, SimpleText, ComponentWrapper =
    render.FormattedStringBuffer, core.Object, render.SimpleText, render.ComponentWrapper
local TextLayout =
    render.TextLayout

local function copyAttrs(target, v)
    if v.font         then target.font         = getFont(v.font) end
    if v.relativeSize then target.relativeSize = v.relativeSize  end
    if v.color        then target.color        = v.color         end
    return target
end

local function fontAccessor(component, underlying)
    component._property("font", function() return getFontName(underlying.font) end,
                                function(v) underlying.font = getFont(v) end)
end

function TextFormatter()
    local underlying = FormattedStringBuffer()
    local formatter  = Object()

    fontAccessor(formatter, underlying)
    inheritLockedProperty(formatter, underlying, "relativeSize", "color")
    inheritUnboundMethod(formatter, underlying, "getFormattedString", "paragraphBreak", "lineBreak", "bulletStop",
                                                "noStartLineHint")

    formatter._property("attributes", function()
        return {font         = getFontName(underlying.font),
                relativeSize = underlying.relativeSize,
                color        = underlying.color}
    end, function(v)
        copyAttrs(underlying, v)
    end)
    formatter._method("append", function(str, attrs)
        if attrs then
            if attrs.font then attrs = copyAttrs({}, attrs) end
            return underlying:appendWithAttributes(str, attrs)
        else
            return underlying:append(str)
        end
    end)

    lockAll(formatter)

    return formatter
end

function component.SimpleText(string, font, size, color)
    font = getFont(font)
    local underlying = SimpleText(string, font, size, color)
    local wrapper = ComponentWrapper(underlying)
    fontAccessor(wrapper, underlying)
    inheritProperty(wrapper, underlying, "text", "fontSize", "color")
    return wrapper
end

-- TODO: Do a proper wrapper around the text scaling features

component.SimpleFormattedText = render.SimpleFormattedText

component.TextLayout = TextLayout

local function SingleTextLayout(bounds)
    local underlying = TextLayout(bounds)
    local wrapper = ComponentWrapper(underlying)

    inheritProperty(wrapper, underlying, "lineBreakSize", "paragraphBreakSize", "bulletStopOffset", "centerVertical",
                                         "centerVerticalCycles", "fontSize", "tryScaleText", "fontSizeDecrement",
                                         "minFontSize", "tryExtra")

    local areas = underlying.areas
    areas.new("main", bounds)
    local main = areas.main

    inheritLockedProperty(wrapper, main, "text")
    inheritMethod(wrapper, main, "addExclusion")

    for _, name in ipairs({"size", "bounds"}) do
        local underlying_prop_get, underlying_prop_set = underlying._getProperty(name)
        wrapper._property(name, function() return underlying_prop_get() end, function(v)
            underlying_prop_set(v)
            main[name] = v
        end)
    end

    return wrapper
end
component.SingleTextLayout = SingleTextLayout

function component.SimpleTextLayout(bounds, string, font, size, color)
    local underlying = SingleTextLayout(bounds)
    local wrapper = ComponentWrapper(underlying)

    inheritProperty(wrapper, underlying, "lineBreakSize", "centerVertical", "centerVerticalCycles", "startFontSize",
                                         "fontSizeDecrement", "fontSize", "tryScaleText", "tryExtra", "bounds", "size")
    inheritMethod(wrapper, underlying, "addExclusion")

    color = color or {0, 0, 0}

    local function render()
        local layout = FormattedStringBuffer()
        layout.font = getFont(font)
        layout.color = color
        layout:append(string)
        underlying.text = layout:getFormattedString()
    end
    render()

    if size then underlying.fontSize = size end
    wrapper._property("string", function() return string end, function(v) string = v render() end)
    wrapper._property("font"  , function() return font   end, function(v) font   = v render() end)
    wrapper._property("color" , function() return color  end, function(v) color  = v render() end)

    return wrapper
end
