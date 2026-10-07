mod boot_script;
pub mod cli;
mod debug;
mod dynamic_manager;
mod feature;
mod init_event;
#[cfg(all(target_arch = "aarch64", target_os = "android"))]
mod kpm;
mod ksucalls;
mod late_load;
pub mod mcp;
mod module;
mod plugin;
mod plugin_lua;
mod profile;
pub(crate) mod resetprop;
mod restorecon;
mod sepolicy;
mod soft_reboot;
mod su;
mod sulog;
pub mod susfs;
#[allow(nonstandard_style, unused, unsafe_op_in_unsafe_fn)]
pub mod uapi;
mod umount_config;
mod unload;
mod user_ko;
pub mod utils;
