// SPDX-License-Identifier: GPL-3.0-or-later
// Portions Copyright (C) Anatdx (YukiSU)
// Source: https://github.com/Rouyashiki/YukiSU
// Ported into ZakoDaNiuMask; see LICENSE.

use std::fs::File;
use std::io::Read;
use std::path::Path;

use anyhow::{Context, Result, bail};

const AK3_UPDATER: &str = "META-INF/com/google/android/update-binary";
const AK3_SCRIPT: &str = "anykernel.sh";

#[derive(Debug, Default, Clone)]
pub struct Ak3PackageInfo {
    pub valid: bool,
    pub error: String,
    pub kernel_name: String,
    pub devices: Vec<String>,
    pub package_slot_policy: String,
}

fn path_is_safe(name: &str) -> bool {
    !name.starts_with('/') && !name.split('/').any(|component| component == "..")
}

fn strip_shell_value(value: &str) -> String {
    let value = value.trim();
    let value = value.split(" #").next().unwrap_or(value).trim();
    let bytes = value.as_bytes();
    if bytes.len() >= 2 {
        let first = bytes[0];
        let last = bytes[bytes.len() - 1];
        if (first == b'"' && last == b'"') || (first == b'\'' && last == b'\'') {
            return value[1..value.len() - 1].to_string();
        }
    }
    value.to_string()
}

fn parse_package_info(script: &str) -> Ak3PackageInfo {
    let mut info = Ak3PackageInfo {
        valid: true,
        ..Default::default()
    };

    for raw in script.lines() {
        let line = raw.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let Some(equals) = line.find('=') else {
            continue;
        };
        let key = line[..equals].trim();
        let value = strip_shell_value(&line[equals + 1..]);
        if key == "kernel.string" {
            info.kernel_name = value;
        } else if key.starts_with("device.name") && !value.is_empty() {
            info.devices.push(value);
        } else if (key == "SLOT_SELECT" || key == "slot_select") && !value.is_empty() {
            info.package_slot_policy = value;
        }
    }

    info.devices.sort();
    info.devices.dedup();
    info
}

fn inspect_inner(zip_path: &Path) -> Result<Ak3PackageInfo> {
    let file = File::open(zip_path).with_context(|| format!("open {}", zip_path.display()))?;
    let mut archive = zip::ZipArchive::new(file)
        .with_context(|| format!("invalid ZIP archive {}", zip_path.display()))?;

    let mut updater_count = 0u32;
    let mut script_count = 0u32;
    let mut script_index = None;
    for index in 0..archive.len() {
        let entry = archive.by_index(index)?;
        let name = entry.name();
        if !path_is_safe(name) {
            bail!("archive contains an unsafe path: {name}");
        }
        if name == AK3_UPDATER {
            updater_count += 1;
        } else if name == AK3_SCRIPT {
            script_count += 1;
            script_index = Some(index);
        }
    }

    if updater_count != 1 || script_count != 1 {
        bail!("not an AnyKernel3 package (update-binary or anykernel.sh is missing/duplicated)");
    }

    let mut entry = archive
        .by_index(script_index.context("anykernel.sh not found")?)
        .context("failed to open anykernel.sh")?;
    let mut script = String::new();
    entry
        .read_to_string(&mut script)
        .context("failed to read anykernel.sh")?;
    Ok(parse_package_info(&script))
}

pub fn inspect_ak3_package(zip_path: &Path) -> Ak3PackageInfo {
    match inspect_inner(zip_path) {
        Ok(info) => info,
        Err(error) => Ak3PackageInfo {
            valid: false,
            error: format!("{error:#}"),
            ..Default::default()
        },
    }
}

#[cfg(target_os = "android")]
pub fn flash_ak3(zip_path: &Path, slot: Option<crate::anykernel3::Slot>) -> Result<()> {
    crate::anykernel3::flash(zip_path, slot)
}
