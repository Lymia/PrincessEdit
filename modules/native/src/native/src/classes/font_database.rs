/*
 * Copyright (c) 2017-2022 Lymia Alusyia <lymia@lymiahugs.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

use crate::{jni_utils::*, object_id::IdManager};
use fontdb::Database;
use jni::{
    objects::{JClass, JString},
    sys::{jbyteArray, jint},
    JNIEnv,
};
use parking_lot::RwLock;
use std::{path::PathBuf, sync::Arc};

static FONT_DATABASES: IdManager<RwLock<Database>> = IdManager::new();

pub fn get_database(i: jint) -> Arc<RwLock<Database>> {
    FONT_DATABASES.get(i as u32)
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontDatabase_newNative(
    env: JNIEnv,
    _class: JClass<'_>,
) -> jint {
    catch_panic_jint(&env, || {
        FONT_DATABASES.allocate(RwLock::new(Database::new())) as i32
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontDatabase_deleteNative(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
) {
    catch_panic_void(&env, || {
        FONT_DATABASES.free(id as u32);
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontDatabase_addFontByDataNative(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
    data: jbyteArray,
) {
    catch_panic_void(&env, || {
        let db = FONT_DATABASES.get(id as u32);
        let mut db = db.write();
        db.load_font_data(jbytearray_unwrap(&env, data));
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontDatabase_addFontByPathNative(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
    path: JString<'_>,
) {
    catch_panic_void(&env, || {
        let path = PathBuf::from(jstring_unwrap(&env, path));
        let db = FONT_DATABASES.get(id as u32);
        let mut db = db.write();

        if !path.exists() {
            panic!("Path '{}' does not exist!", path.display())
        } else if path.is_file() {
            db.load_font_file(path).expect("Could not load font.");
        } else if path.is_dir() {
            db.load_fonts_dir(path);
        } else {
            panic!("Path '{}' is not a file or a directory!!", path.display())
        }
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontDatabase_loadSystemFontsNative(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
) {
    catch_panic_void(&env, || {
        let db = FONT_DATABASES.get(id as u32);
        let mut db = db.write();
        db.load_system_fonts();
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_fonts_FontDatabase_setDefaultNative(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
    family: jint,
    name: JString<'_>,
) {
    catch_panic_void(&env, || {
        let name = jstring_unwrap(&env, name);
        let db = FONT_DATABASES.get(id as u32);
        let mut db = db.write();
        match family {
            0 => db.set_serif_family(name),
            1 => db.set_sans_serif_family(name),
            2 => db.set_cursive_family(name),
            3 => db.set_fantasy_family(name),
            4 => db.set_monospace_family(name),
            _ => panic!("Unknown family ID '{family}'"),
        }
    })
}
