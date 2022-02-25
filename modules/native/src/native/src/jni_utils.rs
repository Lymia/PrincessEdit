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

use jni::{
    objects::{JObject, JString},
    sys::{jint, jobject},
    JNIEnv,
};
use std::panic::AssertUnwindSafe;

fn catch_panic_ll<T>(env: &JNIEnv, func: impl FnOnce() -> T) -> Option<T> {
    match std::panic::catch_unwind(AssertUnwindSafe(|| func())) {
        Ok(v) => Some(v),
        Err(e) => {
            let msg_str = if let Some(_) = e.downcast_ref::<String>() {
                match e.downcast::<String>() {
                    Ok(s) => *s,
                    Err(_) => "error retrieving string???".to_string(),
                }
            } else if let Some(s) = e.downcast_ref::<&'static str>() {
                s.to_string()
            } else {
                "could not retrieve panic data".to_string()
            };
            if let Err(e) = env.throw_new("moe/lymia/princess/native/NativeException", msg_str) {
                eprintln!("Error throwing native exception: {:?}", e);
                eprintln!("[ aborting ]");
                std::process::abort(); // rip
            }
            None
        }
    }
}

pub fn catch_panic_jobject(env: &JNIEnv, func: impl FnOnce() -> jobject) -> jobject {
    match catch_panic_ll(env, func) {
        Some(v) => v,
        None => JObject::null().into_inner(),
    }
}

pub fn catch_panic_jint(env: &JNIEnv, func: impl FnOnce() -> jint) -> jint {
    match catch_panic_ll(env, func) {
        Some(v) => v,
        None => -1,
    }
}
pub fn catch_panic_void(env: &JNIEnv, func: impl FnOnce()) {
    catch_panic_ll(env, func);
}

pub fn jstring_unwrap(env: &JNIEnv, str: JString<'_>) -> String {
    env.get_string(str)
        .expect("could not convert Java string to Rust string!!")
        .into()
}
