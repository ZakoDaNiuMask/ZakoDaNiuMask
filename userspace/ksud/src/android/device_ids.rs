// Ported from Duck-ToolBox (MIT), crates/duck-device-ids; modified for ZakoDaNiuMask.
//
//! Provisions attestation device IDs (brand, model, IMEI, ...) into the Qualcomm
//! Keymaster trusted application through `libQSEEComAPI.so`.
//!
//! The tag values are the KeyMint `ATTESTATION_ID_*` tags from AOSP
//! `hardware/interfaces/security/keymint/aidl/android/hardware/security/keymint/Tag.aidl`.
//!
//! This is the only place in ksud permitted to dereference a foreign shared buffer
//! owned by the trusted application, so all `unsafe` is confined to this module.

use std::ffi::{CStr, CString, c_char, c_int, c_void};
use std::time::Duration;
use std::{ptr, slice, thread};

use anyhow::{Context, Result, anyhow, bail};
use clap::{Args, Subcommand};
use serde::Serialize;

use crate::android::utils;

#[allow(clippy::large_enum_variant)]
#[derive(Subcommand, Debug)]
pub enum DeviceIdsCommand {
    /// Print the identifiers detected from system properties as JSON
    Defaults,
    /// Write device IDs into the Qualcomm Keymaster trusted application
    Provision(ProvisionArgs),
}

#[derive(Args, Debug)]
pub struct ProvisionArgs {
    #[arg(long)]
    pub brand: Option<String>,
    #[arg(long)]
    pub device: Option<String>,
    #[arg(long)]
    pub product: Option<String>,
    #[arg(long)]
    pub serial: Option<String>,
    #[arg(long)]
    pub manufacturer: Option<String>,
    #[arg(long)]
    pub model: Option<String>,
    #[arg(long)]
    pub imei: Option<String>,
    #[arg(long)]
    pub imei2: Option<String>,
    #[arg(long)]
    pub meid: Option<String>,
    #[arg(long)]
    pub meid2: Option<String>,
    /// Trusted application name (default: keymaster64)
    #[arg(long)]
    pub ta_name: Option<String>,
    /// Trusted application directory (default: /vendor/firmware_mnt/image)
    #[arg(long)]
    pub ta_path: Option<String>,
    /// Build and validate the command without sending it to Keymaster
    #[arg(long)]
    pub dry_run: bool,
}

impl ProvisionArgs {
    fn into_profile(self) -> DeviceIdsProfile {
        DeviceIdsProfile {
            brand: self.brand.unwrap_or_default(),
            device: self.device.unwrap_or_default(),
            product: self.product.unwrap_or_default(),
            serial: self.serial.unwrap_or_default(),
            manufacturer: self.manufacturer.unwrap_or_default(),
            model: self.model.unwrap_or_default(),
            imei: self.imei.unwrap_or_default(),
            imei2: self.imei2.unwrap_or_default(),
            meid: self.meid.unwrap_or_default(),
            meid2: self.meid2.unwrap_or_default(),
            ta_name: self.ta_name.unwrap_or_else(|| DEFAULT_TA_NAME.to_owned()),
            ta_path: self.ta_path.unwrap_or_else(|| DEFAULT_TA_PATH.to_owned()),
        }
    }
}

pub fn run(command: DeviceIdsCommand) -> Result<()> {
    match command {
        DeviceIdsCommand::Defaults => {
            let profile = detect_defaults();
            println!("{}", serde_json::to_string(&profile)?);
        }
        DeviceIdsCommand::Provision(args) => {
            let dry_run = args.dry_run;
            let profile = args.into_profile();
            let result = provision(profile, dry_run)?;
            println!("{}", serde_json::to_string(&result)?);
        }
    }
    Ok(())
}

const SHARED_BUF_SIZE: usize = 0xA000;
/// `QSEECOM_ALIGN_SIZE` from the kernel's `qseecom_kernel.h`. AOSP's Qualcomm keymaster HAL
/// (`hardware/qcom/keymaster`) places the response at `QSEECOM_ALIGN(command length)` so
/// command and response never share a cache line of the shared buffer.
const QSEECOM_ALIGN_SIZE: usize = 0x40;
const DEFAULT_LIB_PATH: &str = "/vendor/lib64/libQSEEComAPI.so";
const DEFAULT_LIB_PATH_ALT: &str = "/vendor/lib64/hw/libQSEEComAPI.so";
const DEFAULT_TA_NAME: &str = "keymaster64";
const DEFAULT_TA_PATH: &str = "/vendor/firmware_mnt/image";
const FALLBACK_TA_NAME: &str = "keymaster";

const CMD_GET_VERSION: u32 = 0x0200;
const CMD_SET_VERSION: u32 = 0x0207;
const CMD_PROVISION_DEVICE_IDS: u32 = 0x220A;
const CMD_SET_PROVISIONING_DEVICE_ID_SUCCESS: u32 = 0x2218;

const KM_TAG_ATTESTATION_ID_BRAND: u32 = 0x9000_02C6;
const KM_TAG_ATTESTATION_ID_DEVICE: u32 = 0x9000_02C7;
const KM_TAG_ATTESTATION_ID_PRODUCT: u32 = 0x9000_02C8;
const KM_TAG_ATTESTATION_ID_SERIAL: u32 = 0x9000_02C9;
const KM_TAG_ATTESTATION_ID_IMEI: u32 = 0x9000_02CA;
const KM_TAG_ATTESTATION_ID_MEID: u32 = 0x9000_02CB;
const KM_TAG_ATTESTATION_ID_MANUFACTURER: u32 = 0x9000_02CC;
const KM_TAG_ATTESTATION_ID_MODEL: u32 = 0x9000_02CD;
const KM_TAG_ATTESTATION_ID_SECOND_IMEI: u32 = 0x9000_02CE;

#[derive(Debug, Clone, Serialize)]
pub struct DeviceIdsProfile {
    pub brand: String,
    pub device: String,
    pub product: String,
    pub serial: String,
    pub manufacturer: String,
    pub model: String,
    pub imei: String,
    pub imei2: String,
    pub meid: String,
    pub meid2: String,
    pub ta_name: String,
    pub ta_path: String,
}

impl Default for DeviceIdsProfile {
    fn default() -> Self {
        Self {
            brand: String::new(),
            device: String::new(),
            product: String::new(),
            serial: String::new(),
            manufacturer: String::new(),
            model: String::new(),
            imei: String::new(),
            imei2: String::new(),
            meid: String::new(),
            meid2: String::new(),
            ta_name: DEFAULT_TA_NAME.to_owned(),
            ta_path: DEFAULT_TA_PATH.to_owned(),
        }
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct ProvisionedId {
    pub label: String,
    pub value: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct ProvisionResult {
    pub count: usize,
    pub ids: Vec<ProvisionedId>,
    pub dry_run: bool,
    pub ta_name: String,
    pub ta_path: String,
    pub loaded_library: Option<String>,
    pub ta_api_version: Option<String>,
    pub ta_version: Option<String>,
    pub command_hex: String,
    pub response_hex: Option<String>,
}

struct DeviceIdSpec {
    tag: u32,
    label: &'static str,
    field: &'static str,
    required: bool,
    value: fn(&DeviceIdsProfile) -> &str,
}

/// Order matches the reference provisioning tool; the TA expects this sequence.
/// Both MEID slots use the same tag because the TA has no dedicated second-MEID tag.
const DEVICE_ID_SPECS: &[DeviceIdSpec] = &[
    DeviceIdSpec {
        tag: KM_TAG_ATTESTATION_ID_BRAND,
        label: "BRAND",
        field: "brand",
        required: true,
        value: |p| &p.brand,
    },
    DeviceIdSpec {
        tag: KM_TAG_ATTESTATION_ID_DEVICE,
        label: "DEVICE",
        field: "device",
        required: true,
        value: |p| &p.device,
    },
    DeviceIdSpec {
        tag: KM_TAG_ATTESTATION_ID_PRODUCT,
        label: "PRODUCT",
        field: "product",
        required: true,
        value: |p| &p.product,
    },
    DeviceIdSpec {
        tag: KM_TAG_ATTESTATION_ID_SERIAL,
        label: "SERIAL",
        field: "serial",
        required: true,
        value: |p| &p.serial,
    },
    DeviceIdSpec {
        tag: KM_TAG_ATTESTATION_ID_IMEI,
        label: "IMEI",
        field: "imei",
        required: false,
        value: |p| &p.imei,
    },
    DeviceIdSpec {
        tag: KM_TAG_ATTESTATION_ID_SECOND_IMEI,
        label: "IMEI2",
        field: "imei2",
        required: false,
        value: |p| &p.imei2,
    },
    DeviceIdSpec {
        tag: KM_TAG_ATTESTATION_ID_MEID,
        label: "MEID",
        field: "meid",
        required: false,
        value: |p| &p.meid,
    },
    DeviceIdSpec {
        tag: KM_TAG_ATTESTATION_ID_MEID,
        label: "MEID2",
        field: "meid2",
        required: false,
        value: |p| &p.meid2,
    },
    DeviceIdSpec {
        tag: KM_TAG_ATTESTATION_ID_MANUFACTURER,
        label: "MANUFACTURER",
        field: "manufacturer",
        required: true,
        value: |p| &p.manufacturer,
    },
    DeviceIdSpec {
        tag: KM_TAG_ATTESTATION_ID_MODEL,
        label: "MODEL",
        field: "model",
        required: true,
        value: |p| &p.model,
    },
];

struct SelectedId {
    tag: u32,
    label: &'static str,
    value: String,
}

/// Reads the current identifiers from system properties.
pub fn detect_defaults() -> DeviceIdsProfile {
    DeviceIdsProfile {
        brand: first(&["ro.product.brand", "ro.product.vendor.brand"]),
        device: first(&["ro.product.device", "ro.product.vendor.device"]),
        product: first(&["ro.product.name", "ro.product.vendor.name"]),
        serial: first(&["ro.serialno", "ro.boot.serialno"]),
        manufacturer: first(&["ro.product.manufacturer", "ro.product.vendor.manufacturer"]),
        model: first(&["ro.product.model", "ro.product.vendor.model"]),
        imei: first(&[
            "persist.vendor.radio.imei",
            "persist.radio.imei",
            "vendor.ril.imei",
            "ril.gsm.imei",
            "ro.ril.oem.imei",
            "ro.ril.oem.imei1",
        ]),
        imei2: first(&[
            "persist.vendor.radio.imei2",
            "persist.radio.imei2",
            "vendor.ril.imei2",
            "ril.gsm.imei2",
            "ro.ril.oem.imei2",
        ]),
        meid: first(&[
            "persist.vendor.radio.meid",
            "persist.radio.meid",
            "vendor.ril.meid",
            "ro.ril.oem.meid",
        ]),
        meid2: first(&[
            "persist.vendor.radio.meid2",
            "persist.radio.meid2",
            "vendor.ril.meid2",
            "ro.ril.oem.meid2",
        ]),
        ta_name: DEFAULT_TA_NAME.to_owned(),
        ta_path: DEFAULT_TA_PATH.to_owned(),
    }
}

fn first(names: &[&str]) -> String {
    names
        .iter()
        .filter_map(|name| utils::getprop(name))
        .map(|value| value.trim().to_owned())
        .find(|value| !value.is_empty())
        .unwrap_or_default()
}

/// Selects the IDs to provision, erroring when a required field is blank and skipping
/// blank optional ones.
fn collect_ids(profile: &DeviceIdsProfile) -> Result<Vec<SelectedId>> {
    let mut ids = Vec::new();

    for spec in DEVICE_ID_SPECS {
        let value = (spec.value)(profile).trim();
        if value.is_empty() {
            if spec.required {
                bail!(
                    "device field `{}` is required before provisioning",
                    spec.field
                );
            }
            continue;
        }

        ids.push(SelectedId {
            tag: spec.tag,
            label: spec.label,
            value: value.to_owned(),
        });
    }

    Ok(ids)
}

/// Builds the `PROVISION_DEVICE_IDS` command: a little-endian operation id followed by a
/// CBOR map of `{ 22: count, <tag>: <value>, ... }`. Map entries keep insertion order
/// because the trusted application reads them positionally.
fn build_command(ids: &[SelectedId]) -> Result<Vec<u8>> {
    if ids.is_empty() {
        bail!("device ID provisioning requires at least one ID");
    }

    let mut payload = Vec::new();
    cbor_head(&mut payload, 5, (ids.len() + 1) as u64); // map with count + header entry
    cbor_int(&mut payload, 22);
    cbor_int(&mut payload, ids.len() as i64);
    for id in ids {
        cbor_int(&mut payload, i64::from(id.tag as i32));
        cbor_bytes(&mut payload, id.value.as_bytes());
    }

    let mut command = CMD_PROVISION_DEVICE_IDS.to_le_bytes().to_vec();
    command.extend_from_slice(&payload);
    Ok(command)
}

fn cbor_head(out: &mut Vec<u8>, major: u8, value: u64) {
    let head = major << 5;
    if value < 24 {
        out.push(head | value as u8);
    } else if value <= u64::from(u8::MAX) {
        out.push(head | 24);
        out.push(value as u8);
    } else if value <= u64::from(u16::MAX) {
        out.push(head | 25);
        out.extend_from_slice(&(value as u16).to_be_bytes());
    } else if value <= u64::from(u32::MAX) {
        out.push(head | 26);
        out.extend_from_slice(&(value as u32).to_be_bytes());
    } else {
        out.push(head | 27);
        out.extend_from_slice(&value.to_be_bytes());
    }
}

/// KeyMint tags are `u32`s reinterpreted as signed CBOR integers, so tags with the high
/// bit set become negative keys.
fn cbor_int(out: &mut Vec<u8>, value: i64) {
    if value >= 0 {
        cbor_head(out, 0, value as u64);
    } else {
        cbor_head(out, 1, (-1 - value) as u64);
    }
}

fn cbor_bytes(out: &mut Vec<u8>, bytes: &[u8]) {
    cbor_head(out, 2, bytes.len() as u64);
    out.extend_from_slice(bytes);
}

/// Merges blank fields with values detected from system properties.
pub fn with_detected_defaults(mut profile: DeviceIdsProfile) -> DeviceIdsProfile {
    fn fill(target: &mut String, fallback: String) {
        if target.trim().is_empty() {
            *target = fallback;
        }
    }

    let detected = detect_defaults();
    fill(&mut profile.brand, detected.brand);
    fill(&mut profile.device, detected.device);
    fill(&mut profile.product, detected.product);
    fill(&mut profile.serial, detected.serial);
    fill(&mut profile.manufacturer, detected.manufacturer);
    fill(&mut profile.model, detected.model);
    fill(&mut profile.imei, detected.imei);
    fill(&mut profile.imei2, detected.imei2);
    fill(&mut profile.meid, detected.meid);
    fill(&mut profile.meid2, detected.meid2);
    if profile.ta_name.trim().is_empty() {
        profile.ta_name = detected.ta_name;
    }
    if profile.ta_path.trim().is_empty() {
        profile.ta_path = detected.ta_path;
    }
    profile
}

pub fn provision(profile: DeviceIdsProfile, dry_run: bool) -> Result<ProvisionResult> {
    let resolved = with_detected_defaults(profile);
    let ids = collect_ids(&resolved)?;
    let command = build_command(&ids)?;

    let mut session_info = SessionInfo::default();
    if !dry_run {
        session_info = qseecom_provision(&resolved.ta_path, &resolved.ta_name, &command)?;
    }

    let provisioned = ids
        .iter()
        .map(|entry| ProvisionedId {
            label: entry.label.to_owned(),
            value: entry.value.clone(),
        })
        .collect::<Vec<_>>();

    Ok(ProvisionResult {
        count: provisioned.len(),
        ids: provisioned,
        dry_run,
        ta_name: resolved.ta_name,
        ta_path: resolved.ta_path,
        loaded_library: session_info.loaded_library,
        ta_api_version: session_info.ta_api_version,
        ta_version: session_info.ta_version,
        command_hex: to_hex(&command),
        response_hex: session_info.response_hex,
    })
}

fn to_hex(bytes: &[u8]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut out = String::with_capacity(bytes.len() * 2);
    for &byte in bytes {
        out.push(HEX[(byte >> 4) as usize] as char);
        out.push(HEX[(byte & 0x0f) as usize] as char);
    }
    out
}

// ---------------------------------------------------------------------------
// QSEECom FFI
// ---------------------------------------------------------------------------

#[repr(C)]
struct QseeComHandle {
    ion_sbuffer: *mut u8,
}

type StartApp =
    unsafe extern "C" fn(*mut *mut QseeComHandle, *const c_char, *const c_char, u32) -> c_int;
type SendCmd =
    unsafe extern "C" fn(*mut QseeComHandle, *mut c_void, u32, *mut c_void, u32) -> c_int;
type ShutdownApp = unsafe extern "C" fn(*mut *mut QseeComHandle) -> c_int;

#[derive(Debug, Default, Clone)]
struct SessionInfo {
    loaded_library: Option<String>,
    ta_api_version: Option<String>,
    ta_version: Option<String>,
    response_hex: Option<String>,
}

#[derive(Debug, Clone, Copy)]
struct KmVersion {
    ta_api_major: u32,
    ta_api_minor: u32,
    ta_major: u32,
    ta_minor: u32,
}

struct QseecomApi {
    handle: *mut c_void,
    start_app: StartApp,
    send_cmd: SendCmd,
    shutdown_app: ShutdownApp,
    loaded_path: String,
}

impl Drop for QseecomApi {
    fn drop(&mut self) {
        unsafe {
            libc::dlclose(self.handle);
        }
    }
}

impl QseecomApi {
    fn load() -> Result<Self> {
        for candidate in [DEFAULT_LIB_PATH, DEFAULT_LIB_PATH_ALT] {
            let Ok(path) = CString::new(candidate) else {
                continue;
            };
            let handle = unsafe { libc::dlopen(path.as_ptr(), libc::RTLD_NOW | libc::RTLD_LOCAL) };
            if handle.is_null() {
                continue;
            }

            let start_app = unsafe { resolve::<StartApp>(handle, c"QSEECom_start_app") };
            let send_cmd = unsafe { resolve::<SendCmd>(handle, c"QSEECom_send_cmd") };
            let shutdown_app = unsafe { resolve::<ShutdownApp>(handle, c"QSEECom_shutdown_app") };

            let (Some(start_app), Some(send_cmd), Some(shutdown_app)) =
                (start_app, send_cmd, shutdown_app)
            else {
                unsafe {
                    libc::dlclose(handle);
                }
                bail!("QSEEComAPI symbols missing in {candidate}");
            };

            return Ok(Self {
                handle,
                start_app,
                send_cmd,
                shutdown_app,
                loaded_path: candidate.to_owned(),
            });
        }

        bail!("failed to load QSEEComAPI from {DEFAULT_LIB_PATH} or {DEFAULT_LIB_PATH_ALT}")
    }

    fn start_session(&self, ta_path: &str, ta_name: &str) -> Result<QseecomSession<'_>> {
        let handle = self.try_start(ta_path, ta_name).or_else(|error| {
            if ta_name.trim() != FALLBACK_TA_NAME {
                self.try_start(ta_path, FALLBACK_TA_NAME)
                    .with_context(|| format!("fallback to {FALLBACK_TA_NAME} after {error}"))
            } else {
                Err(error)
            }
        })?;

        Ok(QseecomSession { api: self, handle })
    }

    fn try_start(&self, ta_path: &str, ta_name: &str) -> Result<*mut QseeComHandle> {
        let path = CString::new(ta_path.trim()).context("TA path contains NUL byte")?;
        let name = CString::new(ta_name.trim()).context("TA name contains NUL byte")?;
        let mut handle = ptr::null_mut();
        let status = unsafe {
            (self.start_app)(
                &mut handle,
                path.as_ptr(),
                name.as_ptr(),
                SHARED_BUF_SIZE as u32,
            )
        };
        if status != 0 || handle.is_null() {
            bail!("QSEECom_start_app failed with status {status}");
        }

        Ok(handle)
    }
}

unsafe fn resolve<T: Copy>(handle: *mut c_void, name: &CStr) -> Option<T> {
    let symbol = unsafe { libc::dlsym(handle, name.as_ptr()) };
    if symbol.is_null() {
        return None;
    }
    Some(unsafe { std::mem::transmute_copy::<*mut c_void, T>(&symbol) })
}

struct QseecomSession<'a> {
    api: &'a QseecomApi,
    handle: *mut QseeComHandle,
}

impl QseecomSession<'_> {
    fn get_version(&self) -> Result<KmVersion> {
        let response = self.send(&CMD_GET_VERSION.to_le_bytes())?;
        if response.len() < 20 {
            bail!("GET_VERSION returned too little data");
        }
        if read_i32(&response, 0)? != 0 {
            bail!("GET_VERSION failed with status {}", read_i32(&response, 0)?);
        }

        Ok(KmVersion {
            ta_api_major: read_u32(&response, 4)?,
            ta_api_minor: read_u32(&response, 8)?,
            ta_major: read_u32(&response, 12)?,
            ta_minor: read_u32(&response, 16)?,
        })
    }

    fn set_version(&self) -> Result<()> {
        let mut request = Vec::with_capacity(24);
        for value in [CMD_SET_VERSION, 4, 5, 4, 5, 0_u32] {
            request.extend_from_slice(&value.to_le_bytes());
        }
        let response = self.send(&request)?;
        if read_i32(&response, 0)? != 0 {
            bail!("SET_VERSION failed with status {}", read_i32(&response, 0)?);
        }
        Ok(())
    }

    fn set_success_marker(&self) -> Result<()> {
        let response = self.send(&CMD_SET_PROVISIONING_DEVICE_ID_SUCCESS.to_le_bytes())?;
        if read_i32(&response, 0)? != 0 {
            bail!(
                "SET_PROVISIONING_DEVICE_ID_SUCCESS failed with status {}",
                read_i32(&response, 0)?
            );
        }
        Ok(())
    }

    fn send(&self, request: &[u8]) -> Result<Vec<u8>> {
        let rsp_offset = qseecom_align(request.len());
        if rsp_offset >= SHARED_BUF_SIZE {
            bail!("request is too large for QSEECom shared buffer");
        }

        let buffer = unsafe {
            let sbuffer = (*self.handle).ion_sbuffer;
            if sbuffer.is_null() {
                bail!("QSEECom shared buffer is unavailable");
            }
            slice::from_raw_parts_mut(sbuffer, SHARED_BUF_SIZE)
        };
        buffer.fill(0);
        buffer[..request.len()].copy_from_slice(request);

        let response_len = (SHARED_BUF_SIZE - rsp_offset) as u32;
        let status = unsafe {
            (self.api.send_cmd)(
                self.handle,
                buffer.as_mut_ptr().cast(),
                request.len() as u32,
                buffer[rsp_offset..].as_mut_ptr().cast(),
                response_len,
            )
        };
        if status != 0 {
            bail!("QSEECom_send_cmd failed with status {status}");
        }

        Ok(buffer[rsp_offset..].to_vec())
    }
}

impl Drop for QseecomSession<'_> {
    fn drop(&mut self) {
        let mut handle = self.handle;
        unsafe {
            let _ = (self.api.shutdown_app)(&mut handle);
        }
    }
}

/// Loads the API, starts the TA, sends the provisioning command and marks it successful.
fn qseecom_provision(ta_path: &str, ta_name: &str, command: &[u8]) -> Result<SessionInfo> {
    let api = QseecomApi::load()?;
    wait_listeners();

    let session = api.start_session(ta_path, ta_name)?;
    let version = session.get_version()?;
    session.set_version()?;

    let response = session.send(command)?;
    if response.len() < 8 {
        bail!("PROVISION_DEVICE_IDS returned too little data");
    }
    let status = read_i32(&response, 0)?;
    if status != 0 {
        bail!("PROVISION_DEVICE_IDS failed with status {status}");
    }
    let data_len = read_u32(&response, 4)? as usize;
    let available = response.len().saturating_sub(8);
    let response_hex = (data_len > 0)
        .then(|| to_hex(&response[8..8 + data_len.min(available)]))
        .filter(|hex| !hex.is_empty());

    session.set_success_marker()?;

    Ok(SessionInfo {
        loaded_library: Some(api.loaded_path.clone()),
        ta_api_version: Some(format!("{}.{}", version.ta_api_major, version.ta_api_minor)),
        ta_version: Some(format!("{}.{}", version.ta_major, version.ta_minor)),
        response_hex,
    })
}

fn wait_listeners() {
    for _ in 0..50 {
        if utils::getprop("vendor.sys.listeners.registered").as_deref() == Some("true") {
            return;
        }
        thread::sleep(Duration::from_millis(100));
    }
}

/// The kernel's `QSEECOM_ALIGN(x)`: round up to the next multiple of 64.
fn qseecom_align(value: usize) -> usize {
    (value + QSEECOM_ALIGN_SIZE - 1) & !(QSEECOM_ALIGN_SIZE - 1)
}

fn read_u32(bytes: &[u8], offset: usize) -> Result<u32> {
    let chunk = bytes
        .get(offset..offset + 4)
        .ok_or_else(|| anyhow!("missing u32 at offset {offset}"))?;
    Ok(u32::from_le_bytes(
        chunk.try_into().expect("slice is 4 bytes"),
    ))
}

fn read_i32(bytes: &[u8], offset: usize) -> Result<i32> {
    let chunk = bytes
        .get(offset..offset + 4)
        .ok_or_else(|| anyhow!("missing i32 at offset {offset}"))?;
    Ok(i32::from_le_bytes(
        chunk.try_into().expect("slice is 4 bytes"),
    ))
}
