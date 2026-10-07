// SPDX-License-Identifier: GPL-3.0-or-later
//
// Tool table for `ksud mcp`.
//
// Each tool is described by a name, description, permission tier, a JSON schema
// and an argv template. The template is expanded against the call's arguments
// and executed by spawning this same ksud binary (which runs as root because
// `ksud mcp` itself is launched via su). Reusing the CLI keeps tool behaviour
// identical to the documented ksud commands.
//
// Template tokens:
//   literal        -> pushed verbatim
//   %key           -> required scalar argument
//   ?key           -> optional scalar argument (omitted when empty)
//   @flag          -> boolean flag: push --flag when args[flag] == true
//   @flag=%key     -> value flag: push --flag <value> when args[key] non-empty

use std::process::Command;

use anyhow::Result;
use serde_json::{Map, Value, json};

use super::policy::Tier;

pub struct Tool {
    pub name: &'static str,
    pub description: &'static str,
    pub tier: Tier,
    pub schema: fn() -> Value,
    pub argv: &'static [&'static str],
}

const MAX_OUTPUT: usize = 128 * 1024;

fn obj(props: &[(&str, &str, bool, &str)]) -> Value {
    let mut properties = Map::new();
    let mut required = Vec::new();
    for (name, ty, req, desc) in props {
        properties.insert(
            (*name).to_string(),
            json!({ "type": ty, "description": desc }),
        );
        if *req {
            required.push((*name).to_string());
        }
    }
    json!({ "type": "object", "properties": properties, "required": required })
}

fn s<'a>(name: &'a str, desc: &'a str) -> (&'a str, &'a str, bool, &'a str) {
    (name, "string", true, desc)
}
fn n<'a>(name: &'a str, desc: &'a str) -> (&'a str, &'a str, bool, &'a str) {
    (name, "integer", true, desc)
}
fn so<'a>(name: &'a str, desc: &'a str) -> (&'a str, &'a str, bool, &'a str) {
    (name, "string", false, desc)
}
fn bo<'a>(name: &'a str, desc: &'a str) -> (&'a str, &'a str, bool, &'a str) {
    (name, "boolean", false, desc)
}

// Schema shorthands declared as fns because the table stores fn pointers.
macro_rules! schema_fn {
    ($name:ident, [$($p:expr),* $(,)?]) => {
        fn $name() -> Value { obj(&[$($p),*]) }
    };
}

schema_fn!(sch_empty, []);
schema_fn!(sch_id, [s("id", "module / plugin / template id")]);
schema_fn!(sch_pkg, [s("package", "Android package name")]);
schema_fn!(
    sch_feature_get,
    [
        s("id", "feature id or name"),
        bo("config", "read from config file")
    ]
);
schema_fn!(
    sch_feature_set,
    [
        s("id", "feature id or name"),
        n("value", "0 = disable, 1 = enable")
    ]
);
schema_fn!(sch_sepolicy_check, [s("sepolicy", "sepolicy statement(s)")]);
schema_fn!(sch_sepolicy_apply, [s("file", "sepolicy rule file path")]);
schema_fn!(sch_template_get, [s("id", "template id")]);
schema_fn!(
    sch_template_set,
    [s("id", "template id"), s("template", "template string")]
);
schema_fn!(
    sch_profile_sepolicy_set,
    [
        s("package", "package name"),
        s("policy", "policy statements")
    ]
);
schema_fn!(sch_kpm_info, [s("name", "KPM module name")]);
schema_fn!(
    sch_kpm_load,
    [s("path", "KPM module path"), so("args", "module args")]
);
schema_fn!(
    sch_kpm_control,
    [s("name", "KPM module name"), s("args", "control args")]
);
schema_fn!(sch_plugin_log, [s("id", "plugin id")]);
schema_fn!(
    sch_plugin_run,
    [
        s("id", "plugin id"),
        s("function", "callback function name")
    ]
);
schema_fn!(sch_get_sign, [s("apk", "apk path")]);
schema_fn!(
    sch_umount_add,
    [
        s("mnt", "mount point path"),
        so("flags", "umount flags, default 0")
    ]
);
schema_fn!(sch_umount_del, [s("mnt", "mount point path")]);
schema_fn!(
    sch_flash_list,
    [bo("all", "list all partitions"), so("slot", "slot suffix")]
);
schema_fn!(
    sch_flash_info,
    [s("partition", "partition name"), so("slot", "slot suffix")]
);
schema_fn!(sch_flash_kernel, [so("slot", "slot suffix")]);
schema_fn!(
    sch_spoof_uname,
    [
        so("release", "new uname release"),
        so("version", "new uname version")
    ]
);
schema_fn!(
    sch_spoof_cpu,
    [
        so("cpu", "core index or -1 for all"),
        s("midr", "MIDR hex"),
        so("bogomips", "BogoMIPS"),
        so("hwcap", "elf_hwcap hex"),
        so("hwcap2", "elf_hwcap2 hex")
    ]
);
schema_fn!(
    sch_spoof_mem,
    [
        n("total_ram_bytes", "target total RAM bytes, 0 to disable"),
        so("cma_bytes", "target CMA bytes")
    ]
);
schema_fn!(
    sch_insmod,
    [
        s("path", "kernel module .ko path"),
        so("params", "space separated key=val params")
    ]
);
schema_fn!(
    sch_resetprop,
    [s("name", "property name"), s("value", "property value")]
);
schema_fn!(
    sch_flash_image,
    [
        s("image", "image path"),
        s("partition", "target partition"),
        so("slot", "slot suffix"),
        bo("no_verify", "skip verification")
    ]
);
schema_fn!(
    sch_flash_backup,
    [
        s("partition", "partition name"),
        s("output", "output path"),
        so("slot", "slot suffix")
    ]
);
schema_fn!(sch_ak3, [s("zip", "AnyKernel3 zip path")]);
schema_fn!(
    sch_flash_ak3,
    [s("zip", "AnyKernel3 zip path"), so("slot", "slot suffix")]
);
schema_fn!(sch_nuke, [s("mnt", "ext4 sysfs mount path")]);
schema_fn!(
    sch_bootinfo_sub,
    [s(
        "query",
        "current-kmi | supported-kmis | is-ab-device | default-partition | available-partitions"
    )]
);
schema_fn!(sch_slot_suffix, [bo("ota", "toggle to the other slot")]);

const R: Tier = Tier::Read;
const W: Tier = Tier::Write;
const D: Tier = Tier::Danger;

pub const TOOLS: &[Tool] = &[
    // ---- read: status / kernel / boot ----
    Tool {
        name: "ksu.version",
        description: "Show ksud/kernel version.",
        tier: R,
        schema: sch_empty,
        argv: &["--version"],
    },
    Tool {
        name: "ksu.boot_info",
        description: "Query boot info (kmi, ab device, partitions).",
        tier: R,
        schema: sch_bootinfo_sub,
        argv: &["boot-info", "%query"],
    },
    Tool {
        name: "ksu.current_kmi",
        description: "Show the current KMI version.",
        tier: R,
        schema: sch_empty,
        argv: &["boot-info", "current-kmi"],
    },
    Tool {
        name: "ksu.supported_kmis",
        description: "List supported KMI versions.",
        tier: R,
        schema: sch_empty,
        argv: &["boot-info", "supported-kmis"],
    },
    Tool {
        name: "ksu.is_ab_device",
        description: "Whether the device is A/B capable.",
        tier: R,
        schema: sch_empty,
        argv: &["boot-info", "is-ab-device"],
    },
    Tool {
        name: "ksu.default_partition",
        description: "Show the auto-selected boot partition.",
        tier: R,
        schema: sch_empty,
        argv: &["boot-info", "default-partition"],
    },
    Tool {
        name: "ksu.available_partitions",
        description: "List available flash partitions.",
        tier: R,
        schema: sch_empty,
        argv: &["boot-info", "available-partitions"],
    },
    Tool {
        name: "ksu.slot_suffix",
        description: "Show the current/OTA slot suffix.",
        tier: R,
        schema: sch_slot_suffix,
        argv: &["boot-info", "slot-suffix", "@ota"],
    },
    Tool {
        name: "flash.slots",
        description: "Show A/B slot information.",
        tier: R,
        schema: sch_empty,
        argv: &["flash", "slots"],
    },
    Tool {
        name: "flash.partitions",
        description: "List partitions for a slot.",
        tier: R,
        schema: sch_flash_list,
        argv: &["flash", "list", "@all", "@slot=%slot"],
    },
    Tool {
        name: "flash.partition_info",
        description: "Inspect a partition.",
        tier: R,
        schema: sch_flash_info,
        argv: &["flash", "info", "%partition", "@slot=%slot"],
    },
    Tool {
        name: "flash.kernel_version",
        description: "Read the kernel version from a boot partition.",
        tier: R,
        schema: sch_flash_kernel,
        argv: &["flash", "kernel", "@slot=%slot"],
    },
    Tool {
        name: "flash.avb",
        description: "Show AVB/dm-verity status.",
        tier: R,
        schema: sch_empty,
        argv: &["flash", "avb"],
    },
    Tool {
        name: "kernel.umount.list",
        description: "List the kernel umount list.",
        tier: R,
        schema: sch_empty,
        argv: &["kernel", "umount", "list"],
    },
    // ---- read: modules / features / profiles / susfs / kpm / plugins ----
    Tool {
        name: "module.list",
        description: "List installed modules.",
        tier: R,
        schema: sch_empty,
        argv: &["module", "list"],
    },
    Tool {
        name: "feature.list",
        description: "List kernel features.",
        tier: R,
        schema: sch_empty,
        argv: &["feature", "list"],
    },
    Tool {
        name: "feature.get",
        description: "Get a feature value and support status.",
        tier: R,
        schema: sch_feature_get,
        argv: &["feature", "get", "%id", "@config"],
    },
    Tool {
        name: "feature.check",
        description: "Check whether a feature is supported.",
        tier: R,
        schema: sch_id,
        argv: &["feature", "check", "%id"],
    },
    Tool {
        name: "sepolicy.check",
        description: "Check whether sepolicy statements are valid.",
        tier: R,
        schema: sch_sepolicy_check,
        argv: &["sepolicy", "check", "%sepolicy"],
    },
    Tool {
        name: "profile.list_templates",
        description: "List app-profile templates.",
        tier: R,
        schema: sch_empty,
        argv: &["profile", "list-templates"],
    },
    Tool {
        name: "profile.get_template",
        description: "Get an app-profile template.",
        tier: R,
        schema: sch_template_get,
        argv: &["profile", "get-template", "%id"],
    },
    Tool {
        name: "profile.get_sepolicy",
        description: "Get the root-profile sepolicy of a package.",
        tier: R,
        schema: sch_pkg,
        argv: &["profile", "get-sepolicy", "%package"],
    },
    Tool {
        name: "susfs.status",
        description: "Show SuSFS status.",
        tier: R,
        schema: sch_empty,
        argv: &["susfs", "status"],
    },
    Tool {
        name: "susfs.version",
        description: "Show the SuSFS version.",
        tier: R,
        schema: sch_empty,
        argv: &["susfs", "version"],
    },
    Tool {
        name: "umount_config.list",
        description: "List auto-applied umount configs.",
        tier: R,
        schema: sch_empty,
        argv: &["umount-config", "list"],
    },
    Tool {
        name: "kpm.list",
        description: "List loaded KPM modules.",
        tier: R,
        schema: sch_empty,
        argv: &["kpm", "list"],
    },
    Tool {
        name: "kpm.num",
        description: "Number of loaded KPM modules.",
        tier: R,
        schema: sch_empty,
        argv: &["kpm", "num"],
    },
    Tool {
        name: "kpm.info",
        description: "Info of a KPM module.",
        tier: R,
        schema: sch_kpm_info,
        argv: &["kpm", "info", "%name"],
    },
    Tool {
        name: "plugin.list",
        description: "List installed Lua plugins.",
        tier: R,
        schema: sch_empty,
        argv: &["plugin", "list"],
    },
    Tool {
        name: "plugin.log",
        description: "Show a plugin's last execution log.",
        tier: R,
        schema: sch_plugin_log,
        argv: &["plugin", "log", "%id"],
    },
    Tool {
        name: "debug.version",
        description: "Get the ksud/kernel version (debug).",
        tier: R,
        schema: sch_empty,
        argv: &["debug", "version"],
    },
    Tool {
        name: "debug.get_sign",
        description: "Get the apk v2 signature size and hash.",
        tier: R,
        schema: sch_get_sign,
        argv: &["debug", "get-sign", "%apk"],
    },
    // ---- write ----
    Tool {
        name: "module.enable",
        description: "Enable a module.",
        tier: W,
        schema: sch_id,
        argv: &["module", "enable", "%id"],
    },
    Tool {
        name: "module.disable",
        description: "Disable a module.",
        tier: W,
        schema: sch_id,
        argv: &["module", "disable", "%id"],
    },
    Tool {
        name: "module.uninstall",
        description: "Mark a module for uninstall.",
        tier: W,
        schema: sch_id,
        argv: &["module", "uninstall", "%id"],
    },
    Tool {
        name: "module.undo_uninstall",
        description: "Undo a module uninstall mark.",
        tier: W,
        schema: sch_id,
        argv: &["module", "undo-uninstall", "%id"],
    },
    Tool {
        name: "module.action",
        description: "Run a module action.",
        tier: W,
        schema: sch_id,
        argv: &["module", "action", "%id"],
    },
    Tool {
        name: "feature.set",
        description: "Enable/disable a kernel feature.",
        tier: W,
        schema: sch_feature_set,
        argv: &["feature", "set", "%id", "%value"],
    },
    Tool {
        name: "feature.save",
        description: "Persist current feature states to disk.",
        tier: W,
        schema: sch_empty,
        argv: &["feature", "save"],
    },
    Tool {
        name: "sepolicy.apply",
        description: "Apply sepolicy rules from a file.",
        tier: W,
        schema: sch_sepolicy_apply,
        argv: &["sepolicy", "apply", "%file"],
    },
    Tool {
        name: "sepolicy.patch",
        description: "Live-patch a sepolicy statement.",
        tier: W,
        schema: sch_sepolicy_check,
        argv: &["sepolicy", "patch", "%sepolicy"],
    },
    Tool {
        name: "profile.set_template",
        description: "Set an app-profile template.",
        tier: W,
        schema: sch_template_set,
        argv: &["profile", "set-template", "%id", "%template"],
    },
    Tool {
        name: "profile.set_sepolicy",
        description: "Set the root-profile sepolicy of a package.",
        tier: W,
        schema: sch_profile_sepolicy_set,
        argv: &["profile", "set-sepolicy", "%package", "%policy"],
    },
    Tool {
        name: "profile.delete_template",
        description: "Delete an app-profile template.",
        tier: W,
        schema: sch_template_get,
        argv: &["profile", "delete-template", "%id"],
    },
    Tool {
        name: "umount_config.add",
        description: "Add an umount config.",
        tier: W,
        schema: sch_umount_add,
        argv: &["umount-config", "add", "%mnt", "@flags=%flags"],
    },
    Tool {
        name: "umount_config.del",
        description: "Delete an umount config.",
        tier: W,
        schema: sch_umount_del,
        argv: &["umount-config", "del", "%mnt"],
    },
    Tool {
        name: "umount_config.clear",
        description: "Clear all umount configs.",
        tier: W,
        schema: sch_empty,
        argv: &["umount-config", "clear"],
    },
    Tool {
        name: "kpm.load",
        description: "Load a KPM module.",
        tier: W,
        schema: sch_kpm_load,
        argv: &["kpm", "load", "%path", "?args"],
    },
    Tool {
        name: "kpm.unload",
        description: "Unload a KPM module.",
        tier: W,
        schema: sch_kpm_info,
        argv: &["kpm", "unload", "%name"],
    },
    Tool {
        name: "kpm.control",
        description: "Send a control command to a KPM module.",
        tier: W,
        schema: sch_kpm_control,
        argv: &["kpm", "control", "%name", "%args"],
    },
    Tool {
        name: "plugin.enable",
        description: "Enable a Lua plugin.",
        tier: W,
        schema: sch_id,
        argv: &["plugin", "enable", "%id"],
    },
    Tool {
        name: "plugin.disable",
        description: "Disable a Lua plugin.",
        tier: W,
        schema: sch_id,
        argv: &["plugin", "disable", "%id"],
    },
    Tool {
        name: "plugin.run",
        description: "Run a plugin callback.",
        tier: W,
        schema: sch_plugin_run,
        argv: &["plugin", "run", "%id", "%function"],
    },
    Tool {
        name: "plugin.action",
        description: "Run a plugin's action callback.",
        tier: W,
        schema: sch_id,
        argv: &["plugin", "action", "%id"],
    },
    Tool {
        name: "kernel.spoof_uname",
        description: "Spoof kernel uname release/version.",
        tier: W,
        schema: sch_spoof_uname,
        argv: &[
            "kernel",
            "spoof-uname",
            "@release=%release",
            "@version=%version",
        ],
    },
    Tool {
        name: "kernel.spoof_cpu",
        description: "Spoof CPU identity (MIDR/BogoMIPS/hwcap).",
        tier: W,
        schema: sch_spoof_cpu,
        argv: &[
            "kernel",
            "spoof-cpu",
            "@cpu=%cpu",
            "@midr=%midr",
            "@bogomips=%bogomips",
            "@hwcap=%hwcap",
            "@hwcap2=%hwcap2",
        ],
    },
    Tool {
        name: "kernel.spoof_mem",
        description: "Spoof total memory capacity.",
        tier: W,
        schema: sch_spoof_mem,
        argv: &[
            "kernel",
            "spoof-mem",
            "@total_ram_bytes=%total_ram_bytes",
            "@cma_bytes=%cma_bytes",
        ],
    },
    Tool {
        name: "insmod",
        description: "Load a kernel module (.ko) with kallsyms access.",
        tier: W,
        schema: sch_insmod,
        argv: &["insmod", "%path", "?params"],
    },
    Tool {
        name: "resetprop.set",
        description: "Set a Magisk-compatible system property.",
        tier: W,
        schema: sch_resetprop,
        argv: &["resetprop", "%name", "%value"],
    },
    Tool {
        name: "soft_reboot",
        description: "Emulate a soft reboot.",
        tier: W,
        schema: sch_empty,
        argv: &["soft-reboot"],
    },
    // ---- danger ----
    Tool {
        name: "flash.image",
        description: "Flash an image to a partition.",
        tier: D,
        schema: sch_flash_image,
        argv: &[
            "flash",
            "image",
            "%image",
            "%partition",
            "@slot=%slot",
            "@no_verify",
        ],
    },
    Tool {
        name: "flash.backup",
        description: "Back up a partition.",
        tier: D,
        schema: sch_flash_backup,
        argv: &["flash", "backup", "%partition", "%output", "@slot=%slot"],
    },
    Tool {
        name: "flash.ak3",
        description: "Flash an AnyKernel3 archive.",
        tier: D,
        schema: sch_flash_ak3,
        argv: &["flash", "ak3", "%zip", "@slot=%slot"],
    },
    Tool {
        name: "anykernel3",
        description: "Flash an AnyKernel3 ZIP.",
        tier: D,
        schema: sch_ak3,
        argv: &["anykernel3", "%zip"],
    },
    Tool {
        name: "kernel.nuke_ext4_sysfs",
        description: "Nuke ext4 sysfs at a mount point.",
        tier: D,
        schema: sch_nuke,
        argv: &["kernel", "nuke-ext4-sysfs", "%mnt"],
    },
    Tool {
        name: "ksu.install",
        description: "Install the KernelSU userspace component to system.",
        tier: D,
        schema: sch_empty,
        argv: &["install"],
    },
    Tool {
        name: "ksu.uninstall",
        description: "Uninstall KernelSU modules and itself (LKM only).",
        tier: D,
        schema: sch_empty,
        argv: &["uninstall"],
    },
    Tool {
        name: "ksu.unload",
        description: "Unload the KernelSU kernel module (LKM only).",
        tier: D,
        schema: sch_empty,
        argv: &["unload"],
    },
];

pub fn find(name: &str) -> Option<&'static Tool> {
    TOOLS.iter().find(|t| t.name == name)
}

pub fn descriptors() -> Value {
    let tools: Vec<Value> = TOOLS
        .iter()
        .map(|t| {
            json!({
                "name": t.name,
                "description": t.description,
                "inputSchema": (t.schema)(),
            })
        })
        .collect();
    json!({ "tools": tools })
}

fn scalar(args: &Map<String, Value>, key: &str) -> Option<String> {
    match args.get(key)? {
        Value::String(v) => Some(v.clone()),
        Value::Number(v) => Some(v.to_string()),
        Value::Bool(v) => Some(v.to_string()),
        _ => None,
    }
}

/// Expand a tool's argv template against the call arguments.
pub fn build_argv(tool: &Tool, args: &Map<String, Value>) -> Result<Vec<String>> {
    let mut out = Vec::new();
    for token in tool.argv {
        if let Some(rest) = token.strip_prefix('@') {
            if let Some((flag, key_expr)) = rest.split_once('=') {
                let key = key_expr.trim_start_matches('%');
                if let Some(value) = scalar(args, key).filter(|value| !value.is_empty()) {
                    out.push(format!("--{}", flag.replace('_', "-")));
                    out.push(value);
                }
            } else if args.get(rest).and_then(Value::as_bool).unwrap_or(false) {
                out.push(format!("--{}", rest.replace('_', "-")));
            }
        } else if let Some(key) = token.strip_prefix('%') {
            let value = scalar(args, key)
                .ok_or_else(|| anyhow::anyhow!("missing required argument '{key}'"))?;
            out.push(value);
        } else if let Some(key) = token.strip_prefix('?') {
            if let Some(value) = scalar(args, key).filter(|value| !value.is_empty()) {
                out.push(value);
            }
        } else {
            out.push((*token).to_string());
        }
    }
    Ok(out)
}

/// Execute a tool by spawning this ksud binary with the expanded argv.
pub fn execute(tool: &Tool, args: &Map<String, Value>) -> Result<(String, bool)> {
    let argv = build_argv(tool, args)?;
    let exe = std::env::current_exe().unwrap_or_else(|_| std::path::PathBuf::from("ksud"));
    log::info!("mcp: exec {exe:?} {argv:?}");

    let output = match Command::new(&exe).args(&argv).output() {
        Ok(output) => output,
        Err(e) => return Ok((format!("failed to run ksud: {e}"), true)),
    };

    let mut text = String::new();
    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    if !stdout.is_empty() {
        text.push_str(stdout.trim_end());
    }
    if !stderr.is_empty() {
        if !text.is_empty() {
            text.push_str("\n\n[stderr]\n");
        }
        text.push_str(stderr.trim_end());
    }
    if text.is_empty() {
        text = format!("(no output; exit code {:?})", output.status.code());
    }
    if text.len() > MAX_OUTPUT {
        text.truncate(MAX_OUTPUT);
        text.push_str("\n...[truncated]");
    }
    let is_error = !output.status.success();
    Ok((text, is_error))
}
