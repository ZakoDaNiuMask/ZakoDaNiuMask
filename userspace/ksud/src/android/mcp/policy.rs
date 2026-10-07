// SPDX-License-Identifier: GPL-3.0-or-later
//
// Tool permission policy for `ksud mcp`.
//
// The policy file is the server-side ceiling: the agent (MCP host) is expected
// to ask the user before a write/danger call, but the kernel-side server is the
// authority on whether that tier is permitted at all. Default is read-only.

use std::collections::BTreeSet;

use anyhow::Result;
use const_format::concatcp;
use serde::Deserialize;

use crate::defs;

const POLICY_PATH: &str = concatcp!(defs::WORKING_DIR, ".mcp_policy.json");

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum Tier {
    Read = 0,
    Write = 1,
    Danger = 2,
}

impl Tier {
    pub fn as_str(self) -> &'static str {
        match self {
            Tier::Read => "read",
            Tier::Write => "write",
            Tier::Danger => "danger",
        }
    }

    fn parse(value: &str) -> Option<Tier> {
        match value.to_ascii_lowercase().as_str() {
            "read" | "readonly" | "read_only" => Some(Tier::Read),
            "write" => Some(Tier::Write),
            "danger" | "full" | "full_auto" => Some(Tier::Danger),
            _ => None,
        }
    }

    /// Parse a user-supplied tier name (also accepts unknown as None).
    pub fn from_name(value: &str) -> Option<Tier> {
        Tier::parse(value)
    }
}

#[derive(Debug, Deserialize, Default)]
struct PolicyFile {
    /// Highest tier the server will execute.
    #[serde(default)]
    max_tier: Option<String>,
    /// Explicit per-tool allow/deny (deny wins). Names are exact tool ids.
    #[serde(default)]
    allow: Vec<String>,
    #[serde(default)]
    deny: Vec<String>,
}

#[derive(Debug, Clone)]
pub struct Policy {
    pub max_tier: Tier,
    allow: BTreeSet<String>,
    deny: BTreeSet<String>,
}

impl Default for Policy {
    fn default() -> Self {
        Policy { max_tier: Tier::Read, allow: BTreeSet::new(), deny: BTreeSet::new() }
    }
}

impl Policy {
    /// Load the on-disk policy, falling back to read-only on any error.
    pub fn load() -> Policy {
        match std::fs::read_to_string(POLICY_PATH) {
            Ok(text) => match serde_json::from_str::<PolicyFile>(&text) {
                Ok(file) => {
                    let max_tier = file
                        .max_tier
                        .as_deref()
                        .and_then(Tier::parse)
                        .unwrap_or(Tier::Read);
                    Policy {
                        max_tier,
                        allow: file.allow.into_iter().collect(),
                        deny: file.deny.into_iter().collect(),
                    }
                }
                Err(e) => {
                    log::warn!("mcp: invalid {POLICY_PATH}: {e}; using read-only");
                    Policy::default()
                }
            },
            Err(_) => Policy::default(),
        }
    }

    /// Whether `name` at `tier` may run.
    pub fn permits(&self, name: &str, tier: Tier) -> bool {
        if self.deny.contains(name) {
            return false;
        }
        if self.allow.contains(name) {
            return true;
        }
        tier <= self.max_tier
    }

    /// Human-readable denial reason for a tool call.
    pub fn denial_reason(&self, name: &str, tier: Tier) -> String {
        if self.deny.contains(name) {
            return format!("tool '{name}' is denied by {POLICY_PATH}");
        }
        format!(
            "tool '{name}' requires tier '{}' but the policy allows at most '{}'; \
raise \"max_tier\" in {POLICY_PATH} (read | write | danger)",
            tier.as_str(),
            self.max_tier.as_str()
        )
    }

    /// Serialize the current policy (as persisted).
    pub fn to_json(&self) -> serde_json::Value {
        serde_json::json!({
            "max_tier": self.max_tier.as_str(),
            "allow": self.allow.iter().cloned().collect::<Vec<_>>(),
            "deny": self.deny.iter().cloned().collect::<Vec<_>>(),
        })
    }

    fn save(&self) -> Result<()> {
        let text = format!("{}\n", serde_json::to_string_pretty(&self.to_json())?);
        std::fs::write(POLICY_PATH, text)?;
        Ok(())
    }

    pub fn set_max_tier(&mut self, tier: Tier) -> Result<()> {
        self.max_tier = tier;
        self.save()
    }

    pub fn allow_tool(&mut self, name: &str) -> Result<()> {
        self.deny.remove(name);
        self.allow.insert(name.to_string());
        self.save()
    }

    pub fn deny_tool(&mut self, name: &str) -> Result<()> {
        self.allow.remove(name);
        self.deny.insert(name.to_string());
        self.save()
    }

    pub fn clear_tool(&mut self, name: &str) -> Result<()> {
        self.allow.remove(name);
        self.deny.remove(name);
        self.save()
    }

    pub fn reset(&mut self) -> Result<()> {
        self.max_tier = Tier::Read;
        self.allow.clear();
        self.deny.clear();
        self.save()
    }
}

/// Write a starter policy if none exists, without clobbering an existing one.
pub fn ensure_default_file() -> Result<()> {
    use std::io::ErrorKind;
    if std::fs::read_to_string(POLICY_PATH).is_ok() {
        return Ok(());
    }
    let content = "{\n  \"max_tier\": \"read\",\n  \"allow\": [],\n  \"deny\": []\n}\n";
    match std::fs::write(POLICY_PATH, content) {
        Ok(()) => Ok(()),
        Err(e) if e.kind() == ErrorKind::PermissionDenied => Ok(()),
        Err(e) => Err(e.into()),
    }
}
