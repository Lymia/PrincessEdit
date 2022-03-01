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

use crate::jni_utils::*;
use fontdb::Database;
use jni::{
    objects::{JClass, JString},
    sys::{jbyteArray, jint},
    JNIEnv,
};
use std::{
    mem,
    path::{Path, PathBuf},
};
use tiny_skia::{Pixmap, Transform};
use usvg::{FitTo, Options, Tree};

fn render_svg(
    w: u32,
    h: u32,
    svg: &str,
    resource_dir: Option<&Path>,
    font_db: &Database,
) -> Vec<u8> {
    let usvg_options = Options::default();
    let mut usvg_options = usvg_options.to_ref();
    usvg_options.resources_dir = resource_dir;
    usvg_options.font_family = "Liberation Sans"; // TODO: should this be hardcoded?
    usvg_options.fontdb = font_db;

    let usvg_tree = Tree::from_str(svg, &usvg_options).expect("Could not parse SVG!");
    let mut pixmap = Pixmap::new(w, h).expect("Could not create pixmap!");
    resvg::render(
        &usvg_tree,
        FitTo::Size(w, h),
        Transform::identity(),
        pixmap.as_mut(),
    );
    pixmap
        .encode_png()
        .expect("Failed to encode pixmap as BMP!")
}

#[no_mangle]
pub extern "system" fn Java_moe_lymia_princess_native_svg_Resvg_renderNative(
    env: JNIEnv,
    _class: JClass<'_>,
    input: JString<'_>,
    resource_path: JString<'_>,
    font_db: jint,
    w: jint,
    h: jint,
) -> jbyteArray {
    catch_panic_jobject(&env, || {
        let input = jstring_unwrap(&env, input);
        let resource_path = if resource_path.is_null() {
            None
        } else {
            Some(PathBuf::from(jstring_unwrap(&env, resource_path)))
        };

        assert!(w > 0 && h > 0);
        let db = crate::classes::font_database::get_database(font_db);
        let db = db.read();
        let rendered = render_svg(
            w as u32,
            h as u32,
            &input,
            resource_path.as_ref().map(|x| x.as_path()),
            &db,
        );
        mem::drop(db);

        env.byte_array_from_slice(&rendered)
            .expect("Could not encode as byte array!")
    })
}
