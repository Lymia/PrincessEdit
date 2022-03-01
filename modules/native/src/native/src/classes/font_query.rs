use fontdb::{Family, Query, Stretch, Style, Weight};
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jint;
use parking_lot::RwLock;
use crate::jni_utils::*;
use crate::object_id::IdManager;

static FONT_QUERIES: IdManager<RwLock<OwnedQuery>> = IdManager::new();

pub enum OwnedFamily {
    Owned(String),
    Static(Family<'static>),
}
pub struct OwnedQuery {
    families: Vec<OwnedFamily>,
    weight: Weight,
    stretch: Stretch,
    style: Style,
}
impl OwnedQuery {
    pub fn with_query<T>(&self, func: impl FnOnce(&Query<'_>) -> T) -> T {
        let family_borrowed: Vec<_> = self.families.iter().map(|x| match x {
            OwnedFamily::Owned(owned) => Family::Name(&owned),
            OwnedFamily::Static(borrowed) => *borrowed,
        }).collect();
        let query = Query {
            families: &family_borrowed,
            weight: self.weight,
            stretch: self.stretch,
            style: self.style,
        };
        func(&query)
    }
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontQuery_newNative(
    env: JNIEnv,
    _class: JClass<'_>,
) -> jint {
    catch_panic_jint(&env, || {
        FONT_QUERIES.allocate(RwLock::new(OwnedQuery {
            families: Vec::new(),
            weight: Weight::NORMAL,
            stretch: Stretch::Normal,
            style: Style::Normal,
        })) as jint
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontQuery_deleteNative(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
) {
    catch_panic_void(&env, || {
        FONT_QUERIES.free(id as u32);
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontQuery_addFamilyNative(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
    name: JString<'_>,
) {
    catch_panic_void(&env, || {
        let name = jstring_unwrap(&env, name);
        let query = FONT_QUERIES.get(id as u32);
        let mut query = query.write();
        query.families.push(OwnedFamily::Owned(name))
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontQuery_addStaticFamilyNative(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
    family: jint,
) {
    catch_panic_void(&env, || {
        let query = FONT_QUERIES.get(id as u32);
        let mut query = query.write();
        query.families.push(match family {
            0 => OwnedFamily::Static(Family::Serif),
            1 => OwnedFamily::Static(Family::SansSerif),
            2 => OwnedFamily::Static(Family::Cursive),
            3 => OwnedFamily::Static(Family::Fantasy),
            4 => OwnedFamily::Static(Family::Monospace),
            _ => panic!("Unknown static font family id '{family}'"),
        })
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontQuery_setWeightNative(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
    weight: jint,
) {
    catch_panic_void(&env, || {
        let query = FONT_QUERIES.get(id as u32);
        let mut query = query.write();
        assert!(
            weight > 0 && weight <= u16::max_value() as jint,
            "Font width must be unsigned 16-bit number.",
        );
        query.weight = Weight(weight as u16);
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontQuery_setStretchNative(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
    stretch: jint,
) {
    catch_panic_void(&env, || {
        let query = FONT_QUERIES.get(id as u32);
        let mut query = query.write();
        query.stretch = match stretch {
            0 => Stretch::UltraCondensed,
            1 => Stretch::ExtraCondensed,
            2 => Stretch::Condensed,
            3 => Stretch::SemiCondensed,
            4 => Stretch::Normal,
            5 => Stretch::SemiExpanded,
            6 => Stretch::Expanded,
            7 => Stretch::ExtraExpanded,
            8 => Stretch::UltraExpanded,
            _ => panic!("Unknown stretch factor id '{stretch}'"),
        };
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontQuery_setStyleNative(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
    style: jint,
) {
    catch_panic_void(&env, || {
        let query = FONT_QUERIES.get(id as u32);
        let mut query = query.write();
        query.style = match style {
            0 => Style::Normal,
            1 => Style::Italic,
            2 => Style::Oblique,
            _ => panic!("Unknown stretch factor id '{style}'"),
        };
    })
}