// SPDX-License-Identifier: GPL-3.0-or-later
//
// SecureGuard spoof orchestration. The kernel side (kernel/feature/sg_spoof.c)
// creates the fake /proc/inte_* nodes; this module makes sure the real inte
// module is unloaded first, drives the toggle ioctl, and persists the setting
// across reboots via a marker file read in on_post_fs_data.

use std::{fs, path::Path};

use anyhow::{Context, Result};
use const_format::concatcp;
use rustix::system;

use crate::{
    android::{ksucalls, uapi},
    defs,
};

const SG_SPOOF_MARKER: &str = concatcp!(defs::WORKING_DIR, ".sg_spoof");
const SG_BLOCK_TARGET: &str = "inte";

fn is_enabled() -> bool {
    fs::exists(SG_SPOOF_MARKER).unwrap_or(false)
}

fn set_enabled_marker(enabled: bool) -> Result<()> {
    let path = Path::new(SG_SPOOF_MARKER);
    if enabled {
        fs::write(path, "1").with_context(|| format!("write {}", path.display()))?;
    } else if path.exists() {
        fs::remove_file(path).with_context(|| format!("remove {}", path.display()))?;
    }
    Ok(())
}

fn unload_target() {
    if let Err(e) = system::delete_module(c"inte", 0) {
        log::warn!("sg-spoof: delete_module {SG_BLOCK_TARGET} failed: {e}");
    } else {
        log::info!("sg-spoof: {SG_BLOCK_TARGET} unloaded");
    }
}

fn apply_ioctl(enabled: bool) -> Result<()> {
    let mut cmd = uapi::ksu_extra_feature_cmd {
        value: if enabled { 1 } else { 0 },
        query: 0,
    };
    ksucalls::ksuctl(uapi::KSU_IOCTL_SG_SPOOF, &raw mut cmd).with_context(|| "sg-spoof ioctl")?;
    Ok(())
}

pub fn enable() -> Result<()> {
    unload_target();
    apply_ioctl(true)?;
    set_enabled_marker(true)?;
    log::info!("sg-spoof: enabled");
    Ok(())
}

pub fn disable() -> Result<()> {
    apply_ioctl(false)?;
    set_enabled_marker(false)?;
    log::info!("sg-spoof: disabled");
    Ok(())
}

pub fn status() -> Result<()> {
    let mut cmd = uapi::ksu_extra_feature_cmd { value: 0, query: 1 };
    ksucalls::ksuctl(uapi::KSU_IOCTL_SG_SPOOF, &raw mut cmd).with_context(|| "sg-spoof ioctl")?;
    println!(
        "sg-spoof: {} (marker: {})",
        if cmd.value != 0 {
            "enabled"
        } else {
            "disabled"
        },
        if is_enabled() { "set" } else { "unset" },
    );
    Ok(())
}

/// Called from on_post_fs_data: reapplies the persisted toggle.
pub fn boot_apply() {
    if !is_enabled() {
        return;
    }
    unload_target();
    if let Err(e) = apply_ioctl(true) {
        log::warn!("sg-spoof: boot apply failed: {e:#}");
    } else {
        log::info!("sg-spoof: boot apply done");
    }
}

pub fn run(command: SgSpoofCommand) -> Result<()> {
    match command {
        SgSpoofCommand::Enable => enable(),
        SgSpoofCommand::Disable => disable(),
        SgSpoofCommand::Status => status(),
    }
}

#[derive(clap::Subcommand, Debug)]
pub enum SgSpoofCommand {
    /// Unload inte, create the fake /proc/inte_* nodes
    Enable,
    /// Remove the fake nodes (inte is NOT re-inserted)
    Disable,
    /// Report whether the spoof is active
    Status,
}
