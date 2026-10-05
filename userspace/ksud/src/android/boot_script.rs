// SPDX-License-Identifier: GPL-3.0-or-later
//
// User-configurable boot script. The manager writes boot_script.sh plus a tiny
// JSON config selecting the boot stage; ksud runs the script once during that
// stage. This keeps user startup scripts first-class instead of relying on the
// generic post-fs-data.d/service.d drop-in directories.

use std::{fs, path::Path};

use log::warn;
use serde::{Deserialize, Serialize};

use crate::{
    android::{
        module::{self, ScriptWait},
        utils::is_safe_mode,
    },
    defs,
};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BootScriptConfig {
    #[serde(default)]
    pub enabled: bool,
    #[serde(default = "default_stage")]
    pub stage: String,
}

fn default_stage() -> String {
    "post-fs-data".to_string()
}

impl Default for BootScriptConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            stage: default_stage(),
        }
    }
}

pub fn load_config() -> BootScriptConfig {
    match fs::read_to_string(defs::BOOT_SCRIPT_CONFIG) {
        Ok(content) => serde_json::from_str(&content).unwrap_or_default(),
        Err(_) => BootScriptConfig::default(),
    }
}

/// Runs the user boot script when it is enabled and configured for `stage`.
pub fn run(stage: &str, wait: ScriptWait) {
    if is_safe_mode() {
        return;
    }

    let config = load_config();
    if !config.enabled || config.stage != stage {
        return;
    }

    let script = Path::new(defs::BOOT_SCRIPT_PATH);
    if !script.exists() {
        warn!(
            "boot script enabled for {stage} but {} is missing",
            script.display()
        );
        return;
    }

    if let Err(e) = module::exec_script(script, wait) {
        warn!("failed to exec boot script for {stage}: {e}");
    }
}
