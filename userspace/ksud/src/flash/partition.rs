// SPDX-License-Identifier: GPL-3.0-or-later
// Portions Copyright (C) Anatdx (YukiSU)
// Source: https://github.com/Rouyashiki/YukiSU
// Ported into ZakoDaNiuMask; see LICENSE.

use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::path::Path;
use std::process::Command;

use anyhow::{Context, Result, bail};

/// Common partition names (shown by default).
pub const COMMON_PARTITIONS: &[&str] = &[
    "boot",
    "init_boot",
    "recovery",
    "dtbo",
    "vbmeta",
    "vendor_boot",
    "vendor_kernel_boot",
];

/// Dangerous partitions that require confirmation.
pub const DANGEROUS_PARTITIONS: &[&str] = &[
    "persist",
    "modem",
    "fsg",
    "bluetooth",
    "dsp",
    "nvram",
    "prodinfo",
    "seccfg",
];

const BY_NAME_DIRS: &[&str] = &[
    "/dev/block/by-name",
    "/dev/block/mapper",
    "/dev/block/bootdevice/by-name",
];

#[derive(Debug, Clone)]
pub struct PartitionInfo {
    pub name: String,
    pub block_device: String,
    pub slot_suffix: String,
    pub is_logical: bool,
    pub size: u64,
    pub exists: bool,
}

fn getprop(name: &str) -> Option<String> {
    let output = Command::new("getprop").arg(name).output().ok()?;
    let value = String::from_utf8_lossy(&output.stdout).trim().to_string();
    (!value.is_empty()).then_some(value)
}

fn run_sh(command: &str) -> (bool, String, String) {
    match Command::new("sh").arg("-c").arg(command).output() {
        Ok(output) => (
            output.status.success(),
            String::from_utf8_lossy(&output.stdout).into_owned(),
            String::from_utf8_lossy(&output.stderr).into_owned(),
        ),
        Err(error) => (false, String::new(), error.to_string()),
    }
}

pub fn read_prop(name: &str) -> Option<String> {
    getprop(name)
}

pub fn get_current_slot_suffix() -> String {
    getprop("ro.boot.slot_suffix").unwrap_or_default()
}

pub fn is_ab_device() -> bool {
    if let Some(value) = getprop("ro.build.ab_update")
        && value.trim().eq_ignore_ascii_case("true")
    {
        return true;
    }
    !get_current_slot_suffix().is_empty()
}

/// Normalize a user provided slot to a suffix such as `_a`.
pub fn normalize_slot_suffix(slot: &str) -> String {
    if slot.is_empty() || slot.starts_with('_') {
        slot.to_string()
    } else {
        format!("_{slot}")
    }
}

pub fn find_partition_block_device(name: &str, slot_suffix: &str) -> Option<String> {
    let mut candidates: Vec<String> = Vec::new();
    if !slot_suffix.is_empty() {
        candidates.push(format!("{name}{slot_suffix}"));
    }
    candidates.push(name.to_string());

    for dir in BY_NAME_DIRS {
        for candidate in &candidates {
            let path = Path::new(dir).join(candidate);
            if path.exists() {
                return Some(path.to_string_lossy().into_owned());
            }
        }
    }
    None
}

fn block_device_size(path: &Path) -> u64 {
    let real = fs::canonicalize(path).unwrap_or_else(|_| path.to_path_buf());
    if let Some(name) = real.file_name().and_then(|value| value.to_str())
        && let Ok(contents) = fs::read_to_string(format!("/sys/class/block/{name}/size"))
        && let Ok(sectors) = contents.trim().parse::<u64>()
    {
        return sectors.saturating_mul(512);
    }
    0
}

fn strip_slot(name: &str, slot_suffix: &str) -> String {
    if !slot_suffix.is_empty() && name.ends_with(slot_suffix) {
        name[..name.len() - slot_suffix.len()].to_string()
    } else {
        name.to_string()
    }
}

pub fn get_all_partitions(slot_suffix: &str) -> Vec<String> {
    let mut names = Vec::new();
    for dir in BY_NAME_DIRS {
        let Ok(entries) = fs::read_dir(dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if name.starts_with("loop") {
                continue;
            }
            names.push(strip_slot(&name, slot_suffix));
        }
    }
    names.sort();
    names.dedup();
    names
}

pub fn get_available_partitions(scan_all: bool, slot_suffix: &str) -> Vec<String> {
    if scan_all {
        return get_all_partitions(slot_suffix);
    }
    COMMON_PARTITIONS
        .iter()
        .filter(|name| find_partition_block_device(name, slot_suffix).is_some())
        .map(|name| (*name).to_string())
        .collect()
}

pub fn is_dangerous_partition(name: &str) -> bool {
    DANGEROUS_PARTITIONS.contains(&name)
}

pub fn get_partition_info(name: &str, slot_suffix: &str) -> PartitionInfo {
    let block_device = find_partition_block_device(name, slot_suffix).unwrap_or_default();
    let exists = !block_device.is_empty();
    let is_logical = block_device.starts_with("/dev/block/mapper/");
    let size = if exists {
        block_device_size(Path::new(&block_device))
    } else {
        0
    };
    PartitionInfo {
        name: name.to_string(),
        block_device,
        slot_suffix: slot_suffix.to_string(),
        is_logical,
        size,
        exists,
    }
}

fn sha256_file(path: &Path) -> Result<String> {
    sha256::try_digest(path).with_context(|| format!("hash {}", path.display()))
}

fn copy_file_to_device(image_path: &Path, block_device: &str) -> Result<()> {
    let mut input =
        File::open(image_path).with_context(|| format!("open {}", image_path.display()))?;
    let mut output = OpenOptions::new()
        .write(true)
        .open(block_device)
        .with_context(|| format!("open {block_device} for writing"))?;
    std::io::copy(&mut input, &mut output).context("failed to write image to partition")?;
    output.sync_all().context("failed to sync partition")?;
    Ok(())
}

fn flash_physical_partition(
    image_path: &Path,
    block_device: &str,
    verify_hash: bool,
) -> Result<()> {
    copy_file_to_device(image_path, block_device)?;
    if verify_hash {
        let source = sha256_file(image_path)?;
        let target = sha256_file(Path::new(block_device))?;
        if source != target {
            bail!("hash mismatch after flashing (source={source}, target={target})");
        }
    }
    Ok(())
}

fn flash_logical_partition(
    image_path: &Path,
    partition_name: &str,
    slot_suffix: &str,
    verify_hash: bool,
) -> Result<()> {
    let image_size = fs::metadata(image_path)
        .with_context(|| format!("stat {}", image_path.display()))?
        .len();
    let full_partition = format!("{partition_name}{slot_suffix}");
    let temp_partition = format!("{partition_name}_ksu_tmp");

    let (ok, _out, err) = run_sh(&format!("lptools create {temp_partition} {image_size}"));
    if !ok {
        bail!("lptools create failed: {err}");
    }

    let _ = run_sh(&format!("lptools unmap {full_partition}"));
    let (ok, _out, err) = run_sh(&format!("lptools map {temp_partition}"));
    if !ok {
        let _ = run_sh(&format!("lptools remove {temp_partition}"));
        bail!("lptools map failed: {err}");
    }

    let temp_block = format!("/dev/block/mapper/{temp_partition}");
    let write_result = copy_file_to_device(image_path, &temp_block);
    if write_result.is_err() {
        let _ = run_sh(&format!("lptools remove {temp_partition}"));
        return write_result;
    }

    if verify_hash {
        let source = sha256_file(image_path)?;
        let target = sha256_file(Path::new(&temp_block))?;
        if source != target {
            let _ = run_sh(&format!("lptools remove {temp_partition}"));
            bail!("hash mismatch after flashing (source={source}, target={target})");
        }
    }

    let (ok, _out, err) = run_sh(&format!(
        "lptools replace {temp_partition} {full_partition}"
    ));
    if !ok {
        let _ = run_sh(&format!("lptools remove {temp_partition}"));
        bail!("lptools replace failed: {err}");
    }
    Ok(())
}

pub fn flash_partition(
    image_path: &Path,
    partition_name: &str,
    slot_suffix: &str,
    verify_hash: bool,
) -> Result<()> {
    let info = get_partition_info(partition_name, slot_suffix);
    if !info.exists {
        bail!("partition {partition_name}{slot_suffix} not found");
    }
    if info.is_logical {
        flash_logical_partition(image_path, partition_name, slot_suffix, verify_hash)
    } else {
        flash_physical_partition(image_path, &info.block_device, verify_hash)
    }
}

pub fn backup_partition(partition_name: &str, output_path: &Path, slot_suffix: &str) -> Result<()> {
    let info = get_partition_info(partition_name, slot_suffix);
    if !info.exists {
        bail!("partition {partition_name}{slot_suffix} not found");
    }
    let source_size = if info.size > 0 {
        info.size
    } else {
        block_device_size(Path::new(&info.block_device))
    };

    let mut input =
        File::open(&info.block_device).with_context(|| format!("open {}", info.block_device))?;
    let mut output =
        File::create(output_path).with_context(|| format!("create {}", output_path.display()))?;
    std::io::copy(&mut input, &mut output).context("failed to back up partition")?;
    output.flush()?;
    output.sync_all()?;

    if source_size > 0 {
        let written = fs::metadata(output_path)?.len();
        if written != source_size {
            bail!("backup size mismatch (expected {source_size}, wrote {written})");
        }
    }
    Ok(())
}

pub fn map_logical_partitions(slot_suffix: &str) -> Result<()> {
    let common_logical = [
        "system",
        "vendor",
        "product",
        "odm",
        "system_ext",
        "vendor_dlkm",
        "odm_dlkm",
    ];

    let mut mapped = 0;
    for base in common_logical {
        let part_name = format!("{base}{slot_suffix}");
        let mapped_path = format!("/dev/block/mapper/{part_name}");
        if Path::new(&mapped_path).exists() {
            mapped += 1;
            continue;
        }
        let _ = run_sh(&format!("dmctl create {part_name}"));
        if Path::new(&mapped_path).exists() {
            mapped += 1;
        }
    }

    if mapped == 0 {
        bail!("mapping failed or no partitions to map");
    }
    Ok(())
}

pub fn get_avb_status() -> String {
    let Some(vbmeta_device) = find_partition_block_device("vbmeta", "") else {
        return String::new();
    };
    let Ok(mut file) = File::open(&vbmeta_device) else {
        return String::new();
    };
    let mut flags = [0u8; 4];
    use std::io::Seek;
    if file.seek(std::io::SeekFrom::Start(123)).is_err() || file.read_exact(&mut flags).is_err() {
        return String::new();
    }
    if flags[0] == 0 && flags[1] == 0 && flags[2] == 0 && (flags[3] == 2 || flags[3] == 3) {
        "disabled".to_string()
    } else {
        "enabled".to_string()
    }
}

pub fn patch_vbmeta_disable_verification() -> Result<()> {
    let vbmeta_device =
        find_partition_block_device("vbmeta", "").context("vbmeta partition not found")?;
    let mut file = OpenOptions::new()
        .write(true)
        .open(&vbmeta_device)
        .with_context(|| format!("open {vbmeta_device}"))?;
    use std::io::Seek;
    file.seek(std::io::SeekFrom::Start(123))?;
    file.write_all(&[0, 0, 0, 3])?;
    file.sync_all()?;
    let _ = Command::new("sync").status();
    Ok(())
}

pub fn get_kernel_version(_slot_suffix: &str) -> String {
    match Command::new("uname").arg("-r").output() {
        Ok(output) if output.status.success() => {
            String::from_utf8_lossy(&output.stdout).trim().to_string()
        }
        _ => String::new(),
    }
}

pub fn get_boot_slot_info() -> String {
    if !is_ab_device() {
        return "{\"is_ab\":false}".to_string();
    }
    let current = get_current_slot_suffix();
    let other = if current == "_a" { "_b" } else { "_a" };
    format!("{{\"is_ab\":true,\"current_slot\":\"{current}\",\"other_slot\":\"{other}\"}}")
}
