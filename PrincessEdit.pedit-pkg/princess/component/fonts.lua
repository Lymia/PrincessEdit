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

local ipairs, warn = ipairs, warn
local loadFont, getExports = _princess.loadFont, module.getExports

local fontCache = {}
local fontList = {}
local function canonicalFontName(family, bold, italic)
    if not bold and not italic then return family end
    bold   = bold   and "Bold"   or ""
    italic = italic and "Italic" or ""
    return family.."-"..bold..italic
end
local function registerFont(name, fontLoader)
    if fontList[name] then
        warn("Font "..name.." already exists!")
    else
        fontList[name] = fontLoader
    end
end
local function loadFontPath(path)
    if fontCache[path] then return fontCache[path] end
    local loaded = loadFont(path)
    fontCache[path] = loaded
    return loaded
end

do
    local fontExports = getExports("font")
    for _, font in ipairs(fontExports) do
        local family, bold, italic = font.getSingle("family"), font.checkFlag("bold"), font.checkFlag("italic")
        local name = canonicalFontName(family, bold, italic)

        local addBold, addItalic = false, false
        if font.checkFlag("derive_italic") then
            if italic then
                warn("Font '"..name.."' is already italic, but 'derive_italic' flag is set.")
            else
                addItalic = true
            end
        end
        if font.checkFlag("derive_bold") then
            if italic then
                warn("Font '"..name.."' is already bold, but 'derive_bold' flag is set.")
            else
                addBold = true
            end
        end

        local fontPath = font.path
        local function loadFont(addBold, addItalic)
            return function()
                local font = loadFontPath(fontPath)
                if addBold   then font = font:bold  (true) end
                if addItalic then font = font:italic(true) end
                return font
            end
        end

        registerFont(name, loadFont(false, false))
        if addBold then registerFont(canonicalFontName(family, true, italic), loadFont(true, false)) end
        if addItalic then registerFont(canonicalFontName(family, bold, true), loadFont(false, true)) end
        if addBold and addItalic then registerFont(canonicalFontName(family, true, true), loadFont(true, true)) end
    end
end

local fontLoaded = {}
local fontNameList = {}
function _princess.getFontName(font)
    return fontNameList[font]
end

local function getFont(fontName)
    local font = fontList[fontName]
    if not font then
        warn("Font '"..fontName.."' does not exist.")
        local type = ""
        for _, suffix in ipairs({"-Bold", "-Italic", "-BoldItalic"}) do
            if fontName:endsWith(suffix) then type = suffix end
        end
        return getFont("FreeSans"..type)
    end

    if not fontLoaded[fontName] then
        font = font()
        fontList[fontName] = font
        fontNameList[font] = fontName
        fontLoaded[fontName] = true
    end

    return font
end
_princess.getFont = getFont