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
use jni::{objects::JClass, sys::jint, JNIEnv};
use parking_lot::RwLock;
use std::sync::Arc;

static FONT_DATABASES: IdManager<RwLock<Database>> = IdManager::new();

pub fn get_database(i: jint) -> Arc<RwLock<Database>> {
    FONT_DATABASES.get(i as u32)
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_Interface_fontDatabaseNew(
    env: JNIEnv,
    _class: JClass<'_>,
) -> jint {
    catch_panic_jint(&env, || {
        FONT_DATABASES.allocate(RwLock::new(Database::new())) as i32
    })
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_Interface_fontDatabaseDelete(
    env: JNIEnv,
    _class: JClass<'_>,
    id: jint,
) {
    catch_panic_void(&env, || {
        FONT_DATABASES.free(id as u32);
    })
}
