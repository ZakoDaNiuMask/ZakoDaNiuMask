use std::path::PathBuf;

use android_logger::Config;
use anyhow::{Context, Ok, Result};
use clap::Parser;
use log::{LevelFilter, error, info};

use crate::{
    android::{
        debug, dynamic_manager, feature, init_event, ksucalls,
        module::{self, module_config, regenerate_preinit_rc},
        profile, sepolicy, su, sulog, susfs, uapi, umount_config, utils,
    },
    anykernel3::{self, Slot},
    apk_sign, assets,
    boot_patch::{BootPatchArgs, BootRestoreArgs},
    defs,
    lkm_image::BootPatchV2Args,
};

/// KernelSU userspace cli
#[derive(Parser, Debug)]
#[command(author, version = defs::FULL_VERSION, about, long_about = None)]
struct Args {
    #[command(subcommand)]
    command: Commands,
}

#[derive(clap::Subcommand, Debug)]
enum Commands {
    /// Flash an AnyKernel3 ZIP
    #[command(name = "anykernel3")]
    AnyKernel3 {
        /// AnyKernel3 ZIP file path
        zip: PathBuf,

        /// Select A/B slot for flashing
        #[arg(long, value_enum)]
        slot: Option<Slot>,
    },

    /// Manage KernelSU modules
    Module {
        #[command(subcommand)]
        command: Module,
    },

    /// Trigger `post-fs-data` event
    PostFsData,

    /// Trigger `service` event
    Services,

    /// Run sulog reader daemon. Not for user. Use `ksud debug sulogd` to launch daemon.
    #[command(hide = true)]
    Sulogd,

    /// Trigger `boot-complete` event
    BootCompleted,

    /// Load kernelsu.ko and execute late-load stage scripts
    LateLoad {
        /// Use adb root to execute late-load for jailbreaking by Magica
        #[arg(long, default_missing_value = "5555", num_args = 0..=1)]
        magica: Option<u16>,

        /// Pass allow_shell=1 when loading kernelsu.ko
        #[arg(long)]
        allow_shell: bool,

        /// Restore adb properties after magica late-load
        #[arg(long)]
        post_magica: bool,

        /// Specify kernel KMI version instead of auto-detection
        #[arg(long)]
        kmi: Option<String>,

        /// manager package name
        #[arg(long, default_value_t = String::from(defs::DEFAULT_PACKAGE_NAME))]
        package_name: String,

        /// kernel release string to spoof
        #[arg(long)]
        spoof_release: Option<String>,

        /// kernel version string to spoof
        #[arg(long)]
        spoof_version: Option<String>,
    },

    /// Manage auto apply user custom umount configs
    UmountConfig {
        #[command(subcommand)]
        command: UmountConfigOp,
    },

    /// Emulate system reboot
    SoftReboot,

    /// Load a kernel module with kallsyms access
    Insmod {
        /// kernel module path
        module: PathBuf,
        /// module load parameters (e.g. key=val key2=val2)
        #[arg(trailing_var_arg = true, allow_hyphen_values = true, num_args = 0..)]
        params: Vec<String>,
    },

    /// Install KernelSU userspace component to system
    Install {
        #[arg(long, default_value = None)]
        libadbroot: Option<PathBuf>,

        #[arg(long, default_value = None)]
        data_path: Option<PathBuf>,
    },

    /// Unload KernelSU kernel module (LKM Only)
    Unload,

    /// Uninstall KernelSU modules and itself(LKM Only)
    Uninstall {
        #[arg(long, default_value_t = String::from("com.zakodaniumask.manager"))]
        package_name: String,
    },

    /// SELinux policy Patch tool
    Sepolicy {
        #[command(subcommand)]
        command: Sepolicy,
    },

    /// Manage App Profiles
    Profile {
        #[command(subcommand)]
        command: Profile,
    },

    /// Manage kernel features
    Feature {
        #[command(subcommand)]
        command: Feature,
    },

    /// Patch boot or init_boot images to apply KernelSU
    BootPatch(BootPatchArgs),

    /// Restore boot or init_boot images patched by KernelSU
    BootRestore(BootRestoreArgs),

    /// Patch KernelSU into a boot image
    ///
    /// Always operates on a boot image; never selects init_boot or vendor_boot.
    BootPatchV2(BootPatchV2Args),

    /// Show boot information
    BootInfo {
        #[command(subcommand)]
        command: BootInfo,
    },

    /// KPM module manager
    #[cfg(all(target_arch = "aarch64", target_os = "android"))]
    Kpm {
        #[command(subcommand)]
        command: kpm_cmd::Kpm,
    },

    /// Manage Lua plugins
    Plugin {
        #[command(subcommand)]
        command: PluginCommand,
    },

    /// Flash and inspect partition images
    Flash {
        #[command(subcommand)]
        command: FlashCommand,
    },

    /// For developers
    Debug {
        #[command(subcommand)]
        command: Debug,
    },
    /// Kernel interface
    Kernel {
        #[command(subcommand)]
        command: Kernel,
    },

    /// Resetprop - Magisk-compatible system property tool
    Resetprop(crate::android::resetprop::Args),

    /// Manage susfs component
    Susfs(susfs::cli::SusfsArgs),

    /// Manage initrc injection
    Initrc {
        #[command(subcommand)]
        command: Initrc,
    },

    /// Run a Model Context Protocol (MCP) server over stdio
    Mcp,
}

#[derive(clap::Subcommand, Debug)]
enum UmountConfigOp {
    /// Add an new umount config to configuration file
    Add {
        /// mount point path
        mnt: String,
        /// umount flags (default: 0, MNT_DETACH: 2)
        #[arg(short, long, default_value = "0")]
        flags: u32,
    },
    /// Delete an umount config from configuration file
    Del {
        /// mount point path
        mnt: String,
    },
    /// Clear all auto apply umount config from configuration file
    Clear,
    /// List all configured auto apply umount configuration
    List,
}

#[derive(clap::Subcommand, Debug)]
enum BootInfo {
    /// show current kmi version
    CurrentKmi,

    /// show supported kmi versions
    SupportedKmis,

    /// check if device is A/B capable
    IsAbDevice,

    /// show auto-selected boot partition name
    DefaultPartition,

    /// list available partitions for current or OTA toggled slot
    AvailablePartitions,

    /// show slot suffix for current or OTA toggled slot
    SlotSuffix {
        /// toggle to another slot
        #[arg(short = 'u', long, default_value = "false")]
        ota: bool,
    },
}

#[derive(clap::Subcommand, Debug)]
enum FlashCommand {
    /// Show A/B slot information
    Slots,

    /// List partitions
    List {
        #[arg(long)]
        slot: Option<String>,
        #[arg(long)]
        all: bool,
    },

    /// Inspect a partition
    Info {
        partition: String,
        #[arg(long)]
        slot: Option<String>,
    },

    /// Back up a partition
    Backup {
        partition: String,
        output: PathBuf,
        #[arg(long)]
        slot: Option<String>,
    },

    /// Flash an image to a partition
    Image {
        image: PathBuf,
        partition: String,
        #[arg(long)]
        slot: Option<String>,
        #[arg(long)]
        no_verify: bool,
    },

    /// Map logical partitions for a slot
    Map { slot: String },

    /// Inspect an AnyKernel3 archive
    Ak3Info { zip: PathBuf },

    /// Flash an AnyKernel3 archive
    Ak3 {
        zip: PathBuf,
        #[arg(long, value_enum)]
        slot: Option<Slot>,
        #[arg(long)]
        log: Option<PathBuf>,
        #[arg(long)]
        use_mkbootfs: bool,
    },

    /// Show AVB/dm-verity status
    Avb { action: Option<String> },

    /// Read the kernel version
    Kernel {
        #[arg(long)]
        slot: Option<String>,
    },

    /// Read boot slot information
    BootInfo,
}

fn resolve_slot(slot: Option<String>) -> String {
    slot.map(|value| crate::flash::partition::normalize_slot_suffix(&value))
        .unwrap_or_else(crate::flash::partition::get_current_slot_suffix)
}

fn run_flash(command: FlashCommand) -> Result<()> {
    use crate::flash::{ak3, partition};

    match command {
        FlashCommand::Slots => {
            if !partition::is_ab_device() {
                println!("This device is not A/B partitioned");
                return Ok(());
            }
            let current = partition::get_current_slot_suffix();
            let other = if current == "_a" { "_b" } else { "_a" };
            println!("Slot Information:");
            println!("  Current slot: {current}");
            println!("  Other slot:   {other}");
            if let Some(value) = partition::read_prop("ro.boot.slot_suffix") {
                println!("  Property ro.boot.slot_suffix: {value}");
            }
        }
        FlashCommand::List { slot, all } => {
            let slot = resolve_slot(slot);
            let partitions = partition::get_available_partitions(all, &slot);
            if all {
                print!("All partitions");
            } else {
                print!("Common partitions");
            }
            if partition::is_ab_device() && !slot.is_empty() {
                print!(" (slot: {slot})");
            }
            println!(":");
            for name in &partitions {
                let info = partition::get_partition_info(name, &slot);
                let kind = if info.is_logical {
                    "logical"
                } else {
                    "physical"
                };
                let marker = if partition::is_dangerous_partition(name) {
                    " [DANGEROUS]"
                } else {
                    ""
                };
                println!("  {name:<20} [{kind}, {} bytes]{marker}", info.size);
            }
        }
        FlashCommand::Info {
            partition: name,
            slot,
        } => {
            let slot = resolve_slot(slot);
            let info = partition::get_partition_info(&name, &slot);
            if !info.exists {
                anyhow::bail!("Partition {name} not found");
            }
            println!("Partition: {}", info.name);
            println!("Block device: {}", info.block_device);
            println!(
                "Type: {}",
                if info.is_logical {
                    "logical"
                } else {
                    "physical"
                }
            );
            println!(
                "Size: {} bytes ({:.2} MB)",
                info.size,
                info.size as f64 / 1024.0 / 1024.0
            );
            if partition::is_ab_device() {
                let display = if info.slot_suffix.is_empty() {
                    "/"
                } else {
                    info.slot_suffix.as_str()
                };
                println!("Slot: {display}");
            }
        }
        FlashCommand::Backup {
            partition: name,
            output,
            slot,
        } => {
            let slot = resolve_slot(slot);
            partition::backup_partition(&name, &output, &slot)
                .map_err(|error| anyhow::anyhow!("Backup failed: {error:#}"))?;
            println!("Backup successful!");
        }
        FlashCommand::Image {
            image,
            partition: name,
            slot,
            no_verify,
        } => {
            let slot = resolve_slot(slot);
            partition::flash_partition(&image, &name, &slot, !no_verify)
                .map_err(|error| anyhow::anyhow!("Flash failed: {error:#}"))?;
            println!("Flash successful!");
        }
        FlashCommand::Map { slot } => {
            let slot = partition::normalize_slot_suffix(&slot);
            partition::map_logical_partitions(&slot)
                .map_err(|error| anyhow::anyhow!("Mapping failed: {error:#}"))?;
            println!("Mapping successful!");
        }
        FlashCommand::Ak3Info { zip } => {
            let info = ak3::inspect_ak3_package(&zip);
            if !info.valid {
                anyhow::bail!("Invalid AnyKernel3 package: {}", info.error);
            }
            println!("valid=1");
            println!("kernel={}", info.kernel_name);
            println!("devices={}", info.devices.join("|"));
            println!("slot_policy={}", info.package_slot_policy);
        }
        FlashCommand::Ak3 {
            zip,
            slot,
            log: _,
            use_mkbootfs: _,
        } => {
            #[cfg(target_os = "android")]
            {
                ak3::flash_ak3(&zip, slot)?;
            }
            #[cfg(not(target_os = "android"))]
            {
                let _ = (zip, slot);
                anyhow::bail!("flash ak3 is only supported on Android");
            }
        }
        FlashCommand::Avb { action } => {
            if action.as_deref() == Some("disable") {
                partition::patch_vbmeta_disable_verification().map_err(|error| {
                    anyhow::anyhow!("Failed to disable AVB/dm-verity: {error:#}")
                })?;
                println!("AVB/dm-verity disabled successfully!");
            } else {
                let status = partition::get_avb_status();
                if status.is_empty() {
                    anyhow::bail!("Failed to get AVB status");
                }
                println!("AVB/dm-verity status: {status}");
            }
        }
        FlashCommand::Kernel { slot } => {
            let slot = resolve_slot(slot);
            let version = partition::get_kernel_version(&slot);
            if version.is_empty() {
                anyhow::bail!("Failed to get kernel version");
            }
            println!("Kernel version: {version}");
        }
        FlashCommand::BootInfo => {
            println!("{}", partition::get_boot_slot_info());
        }
    }

    Ok(())
}

#[derive(clap::Subcommand, Debug)]
enum Debug {
    /// Set the manager app, kernel CONFIG_KSU_DEBUG should be enabled.
    SetManager {
        /// manager package name
        #[arg(default_value_t = String::from(defs::DEFAULT_PACKAGE_NAME))]
        apk: String,
    },

    /// Get apk size and hash
    GetSign {
        /// apk path
        apk: String,
    },

    /// Root Shell
    Su {
        /// switch to gloabl mount namespace
        #[arg(short, long, default_value = "false")]
        global_mnt: bool,
    },

    /// Get kernel version
    Version,

    /// For testing
    Test,

    /// Extract an embedded binary to a specified path
    ExtractBinary {
        /// binary name (e.g. busybox, resetprop, bootctl)
        name: String,
        /// destination file path
        path: PathBuf,
    },

    /// Process mark management
    Mark {
        #[command(subcommand)]
        command: MarkCommand,
    },

    /// Launch sulogd daemon manually
    Sulogd,

    /// Get kernel info
    Info,

    /// Print default package name
    Package,
}

#[derive(clap::Subcommand, Debug)]
enum MarkCommand {
    /// Get mark status for a process (or all)
    Get {
        /// target pid (0 for total count)
        #[arg(default_value = "0")]
        pid: i32,
    },

    /// Mark a process
    Mark {
        /// target pid (0 for all processes)
        #[arg(default_value = "0")]
        pid: i32,
    },

    /// Unmark a process
    Unmark {
        /// target pid (0 for all processes)
        #[arg(default_value = "0")]
        pid: i32,
    },

    /// Refresh mark for all running processes
    Refresh,
}

#[derive(clap::Subcommand, Debug)]
enum Sepolicy {
    /// Patch sepolicy
    Patch {
        /// sepolicy statements
        sepolicy: String,
    },

    /// Apply sepolicy from file
    Apply {
        /// sepolicy file path
        file: String,
    },

    /// Check if sepolicy statement is supported/valid
    Check {
        /// sepolicy statements
        sepolicy: String,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Module {
    /// Install module <ZIP>
    Install {
        /// module zip file path
        zip: String,
    },

    /// Undo module uninstall mark <id>
    UndoUninstall {
        /// module id
        id: String,
    },

    /// Uninstall module <id>
    Uninstall {
        /// module id
        id: String,
    },

    /// enable module <id>
    Enable {
        /// module id
        id: String,
    },

    /// disable module <id>
    Disable {
        // module id
        id: String,
    },

    /// run action for module <id>
    Action {
        // module id
        id: String,
    },

    /// list all modules
    List,

    /// manage module configuration
    Config {
        /// target internal module name (resolved as internal.<name>)
        #[arg(long)]
        internal: Option<String>,
        #[command(subcommand)]
        command: ModuleConfigCmd,
    },
}

#[derive(clap::Subcommand, Debug)]
enum ModuleConfigCmd {
    /// Get a config value
    Get {
        /// config key
        key: String,
    },

    /// Set a config value
    Set {
        /// config key
        key: String,
        /// config value (omit to read from stdin)
        value: Option<String>,
        /// read value from stdin (default if value not provided)
        #[arg(long)]
        stdin: bool,
        /// use temporary config (cleared on reboot)
        #[arg(short, long)]
        temp: bool,
    },

    /// List all config entries
    List,

    /// Delete a config entry
    Delete {
        /// config key
        key: String,
        /// delete from temporary config
        #[arg(short, long)]
        temp: bool,
    },

    /// Clear all config entries
    Clear {
        /// clear temporary config
        #[arg(short, long)]
        temp: bool,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Profile {
    /// get root profile's selinux policy of <package-name>
    GetSepolicy {
        /// package name
        package: String,
    },

    /// set root profile's selinux policy of <package-name> to <profile>
    SetSepolicy {
        /// package name
        package: String,
        /// policy statements
        policy: String,
    },

    /// get template of <id>
    GetTemplate {
        /// template id
        id: String,
    },

    /// set template of <id> to <template string>
    SetTemplate {
        /// template id
        id: String,
        /// template string
        template: String,
    },

    /// delete template of <id>
    DeleteTemplate {
        /// template id
        id: String,
    },

    /// list all templates
    ListTemplates {
        /// print template names instead of ids
        #[arg(short, long)]
        name: bool,

        /// locale used to resolve localized template names (for example, zh_CN)
        #[arg(long, requires = "name")]
        locale: Option<String>,
    },
}

#[derive(clap::Subcommand, Debug)]
enum Feature {
    /// Get feature value and support status
    Get {
        /// Feature ID or name (su_compat, kernel_umount, sulog, adb_root, selinux_hide)
        id: String,
        /// Read from config file
        #[arg(long, default_value_t = false)]
        config: bool,
    },

    /// Set feature value
    Set {
        /// Feature ID or name
        id: String,
        /// Feature value (0=disable, 1=enable)
        value: u64,
    },

    /// List all available features
    List,

    /// Check feature status (supported/unsupported/managed)
    Check {
        /// Feature ID or name (su_compat, kernel_umount, sulog, adb_root, selinux_hide)
        id: String,
    },

    /// Load configuration from file and apply to kernel
    Load,

    /// Save current kernel feature states to file
    Save,
}

#[derive(clap::Subcommand, Debug)]
enum Kernel {
    /// Nuke ext4 sysfs
    NukeExt4Sysfs {
        /// mount point
        mnt: String,
    },
    /// Manage umount list
    Umount {
        #[command(subcommand)]
        command: UmountOp,
    },
    /// Manage dynamic manager
    DynamicManager {
        #[command(subcommand)]
        command: DynamicManagerOp,
    },
    /// Notify that module is mounted
    NotifyModuleMounted,
    /// Spoof the kernel uname (release/version)
    SpoofUname {
        /// new uname release, empty to keep current
        #[arg(long)]
        release: Option<String>,
        /// new uname version, empty to keep current
        #[arg(long)]
        version: Option<String>,
    },
    /// Spoof CPU hardware identity (MIDR/BogoMIPS/hwcap)
    SpoofCpu {
        /// target cpu core index, or -1 for all cores
        #[arg(long, allow_hyphen_values = true, default_value = "-1")]
        cpu: i32,
        /// new MIDR value in hex, e.g. 0x411fd440
        #[arg(long)]
        midr: String,
        /// new BogoMIPS value, 0 to skip
        #[arg(long, default_value = "0")]
        bogomips: u32,
        /// new elf_hwcap in hex, empty to skip
        #[arg(long)]
        hwcap: Option<String>,
        /// new elf_hwcap2 in hex, empty to skip
        #[arg(long)]
        hwcap2: Option<String>,
    },
    /// Spoof total memory capacity (0 to disable)
    SpoofMem {
        /// target total memory size in bytes, e.g. 8589934592 for 8GB, 0 to disable
        #[arg(long)]
        total_ram_bytes: u64,
        /// target total CMA size in bytes, 0 to restore default
        #[arg(long, default_value = "0")]
        cma_bytes: u64,
    },
}

#[derive(clap::Subcommand, Debug)]
enum DynamicManagerOp {
    /// Get the signature of the current dynamic manager (size+hash)
    Get {
        #[arg(long)]
        internal: Option<bool>,
    },
    /// Set the signature of the dynamic manager
    Set {
        /// the signature size
        size: u32,
        /// the signature hash
        #[arg(value_parser = dynamic_manager::parse_hash)]
        hash: [u8; 64],
    },
    /// Set the signature of the dynamic manager for apk
    SetApk {
        /// the apk path
        apk: String,
    },
    /// Clear the dynamic manager
    Clear,
}

#[derive(clap::Subcommand, Debug)]
enum UmountOp {
    /// Add mount point to umount list
    Add {
        /// mount point path
        mnt: String,
        /// umount flags (default: 0, MNT_DETACH: 2)
        #[arg(short, long, default_value = "0")]
        flags: u32,
    },
    /// Delete mount point from umount list
    Del {
        /// mount point path
        mnt: String,
    },
    /// Wipe all entries from umount list
    Wipe,
    /// List all entries from umount list
    List,
}

fn parse_hex_u32(value: &str) -> Result<u32> {
    let trimmed = value
        .trim()
        .trim_start_matches("0x")
        .trim_start_matches("0X");
    u32::from_str_radix(trimmed, 16).with_context(|| format!("invalid hex u32: {value}"))
}

fn parse_hex_u64(value: &str) -> Result<u64> {
    let trimmed = value
        .trim()
        .trim_start_matches("0x")
        .trim_start_matches("0X");
    u64::from_str_radix(trimmed, 16).with_context(|| format!("invalid hex u64: {value}"))
}

fn num_cpus() -> u32 {
    std::thread::available_parallelism()
        .map(|n| n.get() as u32)
        .unwrap_or(1)
}

#[cfg(all(target_arch = "aarch64", target_os = "android"))]
mod kpm_cmd {
    use std::path::PathBuf;

    use clap::Subcommand;

    #[derive(Subcommand, Debug)]
    pub enum Kpm {
        /// Load a KPM module: load <path> [args]
        Load { path: PathBuf, args: Option<String> },
        /// Unload a KPM module: unload <name>
        Unload { name: String },
        /// Get number of loaded modules
        Num,
        /// List loaded KPM modules
        List,
        /// Get info of a KPM module: info <name>
        Info { name: String },
        /// Send control command to a KPM module: control <name> <args>
        Control { name: String, args: String },
        /// Print KPM Loader version
        Version,
    }
}

#[derive(clap::Subcommand, Debug)]
enum PluginCommand {
    /// List installed plugins
    List,
    /// Install a plugin from <ZIP>
    Install {
        /// plugin zip file path
        zip: String,
    },
    /// Uninstall a plugin
    Uninstall {
        /// plugin id
        id: String,
    },
    /// Enable a plugin
    Enable { id: String },
    /// Disable a plugin
    Disable { id: String },
    /// Run a plugin callback
    Run { id: String, function: String },
    /// Run a plugin callback as a background daemon loop
    Daemon {
        /// plugin id
        id: String,
        /// callback function name
        function: String,
        /// loop interval in seconds
        #[arg(default_value_t = 1)]
        interval: u64,
    },
    /// Run the plugin's action callback
    Action { id: String },
    /// Show the last execution log of a plugin
    Log { id: String },
    /// Clear the log of a plugin (or all plugins if id is "all")
    ClearLog { id: String },
    /// Manage plugin user configuration
    Config {
        /// target plugin id
        #[arg(long)]
        id: Option<String>,
        #[command(subcommand)]
        command: PluginConfigCommand,
    },
}

#[derive(clap::Subcommand, Debug)]
enum PluginConfigCommand {
    /// List all config entries of the current plugin
    List,
    /// Read a config entry
    Get { key: String },
    /// Write a config entry
    Set { key: String, value: String },
    /// Delete a config entry
    Delete { key: String },
}

#[derive(clap::Subcommand, Debug)]
enum Initrc {
    /// Regenerate preinit rc file
    Refresh,
}

#[allow(clippy::similar_names)]
pub fn run() -> Result<()> {
    android_logger::init_once(
        Config::default()
            .with_max_level(crate::debug_select!(LevelFilter::Trace, LevelFilter::Info))
            .with_tag("KernelSU"),
    );

    ksucalls::setup_sigsys_handler();

    // the kernel executes su with argv[0] = "su" and replace it with us
    let arg0 = std::env::args().next().unwrap_or_default();
    if arg0 == "su" || arg0.ends_with("/su") {
        return su::root_shell();
    }

    if arg0.ends_with("resetprop") {
        let all_args: Vec<String> = std::env::args().collect();
        return crate::android::resetprop::run_from_args(&all_args);
    }

    if arg0.ends_with("ksu_susfs") {
        let all_args: Vec<String> = std::env::args().collect();
        return crate::android::susfs::cli::run_from_args(&all_args);
    }

    let cli = Args::parse();

    log::info!("command: {:?}", cli.command);

    let result = match cli.command {
        Commands::AnyKernel3 { zip, slot } => anykernel3::flash(&zip, slot),
        Commands::Susfs(args) => crate::android::susfs::cli::run_main(args),
        Commands::PostFsData => init_event::on_post_fs_data(),
        Commands::BootCompleted => {
            init_event::on_boot_completed();
            Ok(())
        }
        Commands::UmountConfig { command } => match command {
            UmountConfigOp::Add { mnt, flags } => umount_config::add_umount(&mnt, flags),
            UmountConfigOp::Del { mnt } => umount_config::del_umount(&mnt),
            UmountConfigOp::Clear => umount_config::wipe_umount(),
            UmountConfigOp::List => umount_config::list_umount(),
        },
        Commands::SoftReboot => crate::android::soft_reboot::soft_reboot(),
        Commands::Insmod { module, params } => debug::insmod(&module, &params),
        Commands::Module { command } => {
            utils::switch_mnt_ns(1)?;
            match command {
                Module::Install { zip } => module::install_module(&zip),
                Module::UndoUninstall { id } => module::undo_uninstall_module(&id),
                Module::Uninstall { id } => module::uninstall_module(&id),
                Module::Enable { id } => module::enable_module(&id),
                Module::Disable { id } => module::disable_module(&id),
                Module::Action { id } => module::run_action(&id),
                Module::List => module::list_modules(),
                Module::Config { internal, command } => {
                    let module_id = match internal {
                        Some(internal_name) => format!("internal.{internal_name}"),
                        None => std::env::var("KSU_MODULE").map_err(|_| {
                            anyhow::anyhow!(
                                "This command must be run in the context of a module or passed --internal <name>"
                            )
                        })?,
                    };
                    crate::android::module::validate_module_id(&module_id)?;

                    match command {
                        ModuleConfigCmd::Get { key } => {
                            // Use merge_configs to respect priority (temp overrides persist)
                            let config = module_config::merge_configs(&module_id)?;
                            match config.get(&key) {
                                Some(value) => {
                                    println!("{value}");
                                    Ok(())
                                }
                                None => anyhow::bail!("Key '{key}' not found"),
                            }
                        }
                        ModuleConfigCmd::Set {
                            key,
                            value,
                            stdin,
                            temp,
                        } => {
                            // Validate key at CLI layer for better user experience
                            module_config::validate_config_key(&key)?;

                            // Read value from stdin or argument
                            let value_str = match value {
                                Some(v) if !stdin => v,
                                _ => {
                                    // Read from stdin
                                    use std::io::Read;
                                    let mut buffer = String::new();
                                    std::io::stdin()
                                        .read_to_string(&mut buffer)
                                        .context("Failed to read from stdin")?;
                                    buffer
                                }
                            };

                            // Validate value
                            module_config::validate_config_value(&value_str)?;

                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::set_config_value(
                                &module_id,
                                &key,
                                &value_str,
                                config_type,
                            )
                        }
                        ModuleConfigCmd::List => {
                            let config = module_config::merge_configs(&module_id)?;
                            if config.is_empty() {
                                println!("No config entries found");
                            } else {
                                for (key, value) in config {
                                    println!("{key}={value}");
                                }
                            }
                            Ok(())
                        }
                        ModuleConfigCmd::Delete { key, temp } => {
                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::delete_config_value(&module_id, &key, config_type)
                        }
                        ModuleConfigCmd::Clear { temp } => {
                            let config_type = if temp {
                                module_config::ConfigType::Temp
                            } else {
                                module_config::ConfigType::Persist
                            };
                            module_config::clear_config(&module_id, config_type)
                        }
                    }
                }
            }
        }
        Commands::Install {
            libadbroot,
            data_path,
        } => utils::install(libadbroot, data_path),
        Commands::Unload => crate::android::unload::unload(),
        Commands::Uninstall { package_name } => utils::uninstall(&package_name),
        Commands::Sepolicy { command } => match command {
            Sepolicy::Patch { sepolicy } => sepolicy::live_patch(&sepolicy),
            Sepolicy::Apply { file } => sepolicy::apply_file(file),
            Sepolicy::Check { sepolicy } => sepolicy::check_rule(&sepolicy),
        },
        Commands::LateLoad {
            magica,
            allow_shell,
            post_magica,
            kmi,
            package_name,
            spoof_release,
            spoof_version,
        } => {
            if let Some(port) = magica {
                return crate::android::late_load::magica::run(port, &package_name, allow_shell)
                    .map_err(|e| {
                        error!("Error running magica: {e}");
                        e
                    });
            }
            let result = crate::android::late_load::run(
                &package_name,
                kmi,
                allow_shell,
                spoof_release.as_deref(),
                spoof_version.as_deref(),
            );
            if post_magica {
                info!("Restoring adb properties (post-magica cleanup)...");
                if let Err(e) = crate::android::late_load::magica::disable_adb_root() {
                    error!("disable adb root failed: {e}");
                }
            }
            result
        }
        Commands::Services => {
            if ksucalls::get_version() <= 0 {
                info!("KernelSU not available, exiting services");
                std::process::exit(0);
            }
            init_event::on_services();
            Ok(())
        }
        Commands::Sulogd => sulog::run_sulogd(),
        Commands::Profile { command } => match command {
            Profile::GetSepolicy { package } => profile::get_sepolicy(package),
            Profile::SetSepolicy { package, policy } => profile::set_sepolicy(package, policy),
            Profile::GetTemplate { id } => profile::get_template(id),
            Profile::SetTemplate { id, template } => profile::set_template(id, template),
            Profile::DeleteTemplate { id } => profile::delete_template(id),
            Profile::ListTemplates { name, locale } => {
                profile::list_templates(name, locale.as_deref())
            }
        },

        Commands::Feature { command } => match command {
            Feature::Get { id, config } => {
                if config {
                    feature::get_feature_config(&id)
                } else {
                    feature::get_feature(&id)
                }
            }
            Feature::Set { id, value } => feature::set_feature(&id, value),
            Feature::List => {
                feature::list_features();
                Ok(())
            }
            Feature::Check { id } => feature::check_feature(&id),
            Feature::Load => feature::load_config_and_apply(),
            Feature::Save => feature::save_config(),
        },

        Commands::Debug { command } => match command {
            Debug::SetManager { apk } => debug::set_manager(&apk),
            Debug::GetSign { apk } => {
                let sign = apk_sign::get_apk_signature(&apk)?;
                println!("size: {:#x}, hash: {}", sign.0, sign.1);
                Ok(())
            }
            Debug::Version => {
                println!("Kernel Version: {}", ksucalls::get_version());
                Ok(())
            }
            Debug::Su { global_mnt } => su::grant_root(global_mnt),
            Debug::Test => assets::ensure_binaries(false),
            Debug::ExtractBinary { name, path } => {
                let data = assets::get_asset(&name)?;
                utils::ensure_binary(&path, data.as_ref(), false)
            }
            Debug::Mark { command } => match command {
                MarkCommand::Get { pid } => debug::mark_get(pid),
                MarkCommand::Mark { pid } => debug::mark_set(pid),
                MarkCommand::Unmark { pid } => debug::mark_unset(pid),
                MarkCommand::Refresh => debug::mark_refresh(),
            },
            Debug::Sulogd => sulog::ensure_sulogd_running(),
            Debug::Info => {
                let info = ksucalls::get_info();
                println!("version: {}", info.version);
                println!("full_version: {}", ksucalls::get_full_version());
                println!("flags: 0x{:x}", info.flags);
                println!("uapi_version: {}", info.uapi_version);
                println!("features: 0x{:x}", info.features);
                println!("lkm: {}", ksucalls::is_lkm());
                println!(
                    "bundled: {}",
                    (info.flags & uapi::KSU_GET_INFO_FLAG_BUNDLED) != 0
                );
                println!("late_load: {}", ksucalls::is_late_load());
                println!("runtime_mode: {}", ksucalls::runtime_mode());
                println!(
                    "pr_build: {}",
                    (info.flags & uapi::KSU_GET_INFO_FLAG_PR_BUILD) != 0
                );
                Ok(())
            }
            Debug::Package => {
                println!("{}", defs::DEFAULT_PACKAGE_NAME);
                Ok(())
            }
        },

        Commands::BootPatch(boot_patch) => crate::boot_patch::patch(boot_patch),

        #[cfg(all(target_arch = "aarch64", target_os = "android"))]
        Commands::Kpm { command } => {
            use kpm_cmd::Kpm;

            use crate::android::kpm;
            match command {
                Kpm::Load { path, args } => {
                    kpm::load_module(path.to_str().unwrap(), args.as_deref())
                }
                Kpm::Unload { name } => kpm::unload_module(name),
                Kpm::Num => kpm::num().map(|_| ()),
                Kpm::List => kpm::list(),
                Kpm::Info { name } => kpm::info(name),
                Kpm::Control { name, args } => {
                    let ret = kpm::control(name, args)?;
                    println!("{ret}");
                    Ok(())
                }
                Kpm::Version => kpm::version(),
            }
        }
        Commands::Plugin { command } => {
            use crate::android::{plugin, plugin_lua};
            match command {
                PluginCommand::List => plugin::list_plugins_json(),
                PluginCommand::Install { zip } => plugin::install_plugin(&zip),
                PluginCommand::Uninstall { id } => plugin::uninstall_plugin(&id),
                PluginCommand::Enable { id } => plugin_lua::set_plugin_state(&id, true),
                PluginCommand::Disable { id } => plugin_lua::set_plugin_state(&id, false),
                PluginCommand::Run { id, function } => {
                    plugin_lua::run_plugin_callback(&id, &function)
                }
                PluginCommand::Daemon {
                    id,
                    function,
                    interval,
                } => plugin_lua::run_plugin_daemon(&id, &function, interval),
                PluginCommand::Action { id } => plugin_lua::run_plugin_callback(&id, "action"),
                PluginCommand::Log { id } => {
                    let log_path = plugin::plugin_path(&id)?.join("last_output.log");
                    match std::fs::read_to_string(&log_path) {
                        std::result::Result::Ok(content) => println!("{content}"),
                        std::result::Result::Err(_) => {
                            println!("No log found for plugin '{id}'");
                        }
                    }
                    Ok(())
                }
                PluginCommand::ClearLog { id } => {
                    if id == "all" {
                        let plugins_dir = std::path::Path::new(crate::defs::PLUGIN_DIR);
                        if plugins_dir.exists() {
                            for entry in std::fs::read_dir(plugins_dir)? {
                                let log_path = entry?.path().join("last_output.log");
                                let _ = std::fs::remove_file(&log_path);
                            }
                        }
                        println!("All plugin logs cleared");
                    } else {
                        let log_path = plugin::plugin_path(&id)?.join("last_output.log");
                        let _ = std::fs::remove_file(&log_path);
                        println!("Log cleared for plugin '{id}'");
                    }
                    Ok(())
                }
                PluginCommand::Config { id, command } => {
                    let plugin_id = match id {
                        Some(value) => value,
                        None => std::env::var("PLUGIN_ID").map_err(|_| {
                            anyhow::anyhow!(
                                "This command must be run in the context of a plugin or passed --id <id>"
                            )
                        })?,
                    };
                    match command {
                        PluginConfigCommand::List => {
                            let map = plugin::read_user_config(&plugin_id)?;
                            println!("{}", serde_json::to_string_pretty(&map)?);
                            Ok(())
                        }
                        PluginConfigCommand::Get { key } => {
                            let map = plugin::read_user_config(&plugin_id)?;
                            if let Some(value) = map.get(&key) {
                                match value {
                                    serde_json::Value::String(s) => println!("{s}"),
                                    other => println!("{other}"),
                                }
                            }
                            Ok(())
                        }
                        PluginConfigCommand::Set { key, value } => {
                            plugin::set_user_config(&plugin_id, &key, &value)
                        }
                        PluginConfigCommand::Delete { key } => {
                            plugin::delete_user_config(&plugin_id, &key)
                        }
                    }
                }
            }
        }
        Commands::BootInfo { command } => match command {
            BootInfo::CurrentKmi => {
                let kmi = crate::boot_patch::get_current_kmi()?;
                println!("{kmi}");
                // return here to avoid printing the error message
                return Ok(());
            }
            BootInfo::SupportedKmis => {
                let kmi = crate::assets::list_supported_kmi();
                for kmi in &kmi {
                    println!("{kmi}");
                }
                return Ok(());
            }
            BootInfo::IsAbDevice => {
                let val =
                    utils::getprop("ro.build.ab_update").unwrap_or_else(|| String::from("false"));
                let is_ab = val.trim().to_lowercase() == "true";
                println!("{}", if is_ab { "true" } else { "false" });
                return Ok(());
            }
            BootInfo::DefaultPartition => {
                let kmi = crate::boot_patch::get_current_kmi().unwrap_or_else(|_| String::new());
                let name = crate::boot_patch::choose_boot_partition(&kmi, false, &None);
                println!("{name}");
                return Ok(());
            }
            BootInfo::SlotSuffix { ota } => {
                let suffix = crate::boot_patch::get_slot_suffix(ota);
                println!("{suffix}");
                return Ok(());
            }
            BootInfo::AvailablePartitions => {
                let parts = crate::boot_patch::list_available_partitions();
                for p in &parts {
                    println!("{p}");
                }
                return Ok(());
            }
        },
        Commands::Flash { command } => run_flash(command),
        Commands::BootRestore(boot_restore) => crate::boot_patch::restore(boot_restore),
        Commands::Resetprop(resetprop_args) => crate::android::resetprop::run(&resetprop_args),
        Commands::BootPatchV2(patch) => crate::lkm_image::patch_boot(&patch),
        Commands::Kernel { command } => match command {
            Kernel::NukeExt4Sysfs { mnt } => ksucalls::nuke_ext4_sysfs(&mnt),
            Kernel::Umount { command } => match command {
                UmountOp::Add { mnt, flags } => ksucalls::umount_list_add(&mnt, flags),
                UmountOp::Del { mnt } => ksucalls::umount_list_del(&mnt),
                UmountOp::Wipe => ksucalls::umount_list_wipe(),
                UmountOp::List => {
                    let list = ksucalls::umount_list_list()?;
                    println!("{}", serde_json::to_string(&list)?);
                    Ok(())
                }
            },
            Kernel::DynamicManager { command } => match command {
                DynamicManagerOp::Set { size, hash } => dynamic_manager::set(size, hash),
                DynamicManagerOp::Get { internal } => {
                    let (size, hash) = ksucalls::dynamic_manager_get()?;
                    if internal.is_some_and(|s| s) {
                        println!(
                            "{}",
                            serde_json::to_string_pretty(
                                &serde_json::json!({"size":size,"hash":String::from_utf8_lossy(&hash)})
                            )?
                        );
                    } else {
                        println!("size: {}, hash: {}", size, String::from_utf8_lossy(&hash));
                    }
                    Ok(())
                }
                DynamicManagerOp::SetApk { apk } => {
                    let sign = apk_sign::get_apk_signature(&apk)?;

                    let bytes = sign.1.as_bytes();

                    let mut hash = [0u8; 64];
                    hash.copy_from_slice(bytes);

                    dynamic_manager::set(sign.0, hash)
                }
                DynamicManagerOp::Clear => dynamic_manager::clear(),
            },
            Kernel::NotifyModuleMounted => {
                ksucalls::report_module_mounted();
                Ok(())
            }
            Kernel::SpoofUname { release, version } => {
                ksucalls::set_spoof_version(
                    release.as_deref().unwrap_or(""),
                    version.as_deref().unwrap_or(""),
                )?;
                println!("kernel spoof-uname done");
                Ok(())
            }
            Kernel::SpoofCpu {
                cpu,
                midr,
                bogomips,
                hwcap,
                hwcap2,
            } => {
                let midr = parse_hex_u32(&midr)?;
                let hwcap = match hwcap.as_deref() {
                    Some(v) if !v.is_empty() => parse_hex_u64(v)?,
                    _ => 0,
                };
                let hwcap2 = match hwcap2.as_deref() {
                    Some(v) if !v.is_empty() => parse_hex_u64(v)?,
                    _ => 0,
                };
                if cpu < 0 {
                    let cores = num_cpus();
                    for index in 0..cores {
                        ksucalls::set_spoof_cpu(index, midr, bogomips, hwcap, hwcap2)?;
                    }
                } else {
                    ksucalls::set_spoof_cpu(cpu as u32, midr, bogomips, hwcap, hwcap2)?;
                }
                println!("kernel spoof-cpu done");
                Ok(())
            }
            Kernel::SpoofMem {
                total_ram_bytes,
                cma_bytes,
            } => {
                ksucalls::set_spoof_mem(total_ram_bytes, cma_bytes)?;
                println!("kernel spoof-mem done");
                Ok(())
            }
        },
        Commands::Initrc { command } => match command {
            Initrc::Refresh => regenerate_preinit_rc(),
        },
        Commands::Mcp => crate::android::mcp::run(),
    };

    if let Err(e) = &result {
        log::error!("Error: {e:?}");
    }
    result
}
