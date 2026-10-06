// SPDX-License-Identifier: GPL-3.0-or-later
//
// User kernel-module (KO / "kernel driver") auto-loader.
//
// The manager stores each module as /data/adb/user_ko/<uuid>.ko and records metadata in
// config.json; ksud loads the entries flagged for auto-load during the configured boot stage,
// reusing ksuinit's ELF loader (kallsyms relocation + init_module + vermagic repair).

use std::{fs, path::Path};

use log::{info, warn};
use serde::{Deserialize, Serialize};

use crate::{android::utils::is_safe_mode, defs};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserKoEntry {
    #[serde(default)]
    pub id: String,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub module_name: String,
    #[serde(default)]
    pub auto_load: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserKoConfig {
    #[serde(default = "default_stage")]
    pub stage: String,
    #[serde(default)]
    pub entries: Vec<UserKoEntry>,
}

fn default_stage() -> String {
    "post-fs-data".to_string()
}

impl Default for UserKoConfig {
    fn default() -> Self {
        Self {
            stage: default_stage(),
            entries: Vec::new(),
        }
    }
}

pub fn load_config() -> UserKoConfig {
    match fs::read_to_string(defs::USER_KO_CONFIG) {
        Ok(content) => serde_json::from_str(&content).unwrap_or_default(),
        Err(_) => UserKoConfig::default(),
    }
}

/// Loads every entry flagged for auto-load when the configured stage matches.
pub fn run(stage: &str) {
    if is_safe_mode() {
        return;
    }

    let config = load_config();
    if config.stage != stage {
        return;
    }

    for entry in &config.entries {
        if !entry.auto_load {
            continue;
        }
        let path = Path::new(defs::USER_KO_DIR).join(format!("{}.ko", entry.id));
        if !path.exists() {
            warn!(
                "user_ko: {} is missing for entry {}",
                path.display(),
                entry.name
            );
            continue;
        }
        match fs::read(&path) {
            Ok(data) => match ksuinit::load_module(&data, c"") {
                Ok(()) => info!(
                    "user_ko: loaded {} [{}] ({})",
                    entry.name,
                    entry.module_name,
                    path.display()
                ),
                Err(e) => warn!("user_ko: load {} failed: {e:#}", path.display()),
            },
            Err(e) => warn!("user_ko: read {} failed: {e}", path.display()),
        }
    }
}
