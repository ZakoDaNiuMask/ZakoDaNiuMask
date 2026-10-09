// Ported from YukiSU (GPL-3.0), userspace/ksud/src/boot/boot_patch.cpp; modified for ZakoDaNiuMask.
//
//! Injects the SuperKey slot into a KernelSU LKM (`.ko`) image.
//!
//! The kernel keeps a 40-byte `superkey_store` in its `.data` section:
//! `magic(8) | salt(16) | hash(8) | flags(8)`. ksud patches it at install time:
//! with no SuperKey the slot is written as all-zero (signature-only), so the
//! kernel keeps verifying the APK signature. Only when a key is supplied does
//! the kernel start accepting password authentication.

use std::io::Read;

use anyhow::{Context, Result};

const SUPERKEY_MAGIC: u64 = 0x53_55_50_45_52; // "SUPER"
const SUPERKEY_SALT_LEN: usize = 16;
const SUPERKEY_BLOCK_LEN: usize = 40;

const MODE_SIGNATURE_ONLY: u64 = 0;
const MODE_SIGN_AND_KEY: u64 = 1;
const MODE_KEY_ONLY: u64 = 2;

/// Rewrites the SuperKey slot inside `bytes` in place. Returns whether the magic
/// was found (a module compiled without SuperKey support has no slot).
pub fn inject(bytes: &mut [u8], superkey: &str, signature_bypass: bool) -> Result<bool> {
    let (salt, hash, flags) = if superkey.is_empty() {
        ([0u8; SUPERKEY_SALT_LEN], 0u64, MODE_SIGNATURE_ONLY)
    } else {
        let mut salt = [0u8; SUPERKEY_SALT_LEN];
        read_random(&mut salt).context("cannot obtain SuperKey salt")?;
        let hash = hash_superkey(&salt, superkey);
        let flags = if signature_bypass {
            MODE_KEY_ONLY
        } else {
            MODE_SIGN_AND_KEY
        };
        (salt, hash, flags)
    };
    Ok(apply(bytes, &salt, hash, flags))
}

/// Patches an on-disk module image, mirroring `inject` but for a file path.
pub fn inject_file(path: &std::path::Path, superkey: &str, signature_bypass: bool) -> Result<bool> {
    let mut bytes = std::fs::read(path)
        .with_context(|| format!("cannot read kernel module {}", path.display()))?;
    let found = inject(&mut bytes, superkey, signature_bypass)?;
    if found {
        std::fs::write(path, &bytes)
            .with_context(|| format!("cannot write patched module {}", path.display()))?;
    }
    Ok(found)
}

fn apply(bytes: &mut [u8], salt: &[u8; SUPERKEY_SALT_LEN], hash: u64, flags: u64) -> bool {
    let magic = SUPERKEY_MAGIC.to_le_bytes();
    let mut offset = 0;
    while offset + SUPERKEY_BLOCK_LEN <= bytes.len() {
        if bytes[offset..offset + 8] == magic {
            bytes[offset + 8..offset + 24].copy_from_slice(salt);
            bytes[offset + 24..offset + 32].copy_from_slice(&hash.to_le_bytes());
            bytes[offset + 32..offset + 40].copy_from_slice(&flags.to_le_bytes());
            return true;
        }
        offset += 1;
    }
    false
}

fn hash_superkey(salt: &[u8; SUPERKEY_SALT_LEN], key: &str) -> u64 {
    let mut input = Vec::with_capacity(SUPERKEY_SALT_LEN + key.len());
    input.extend_from_slice(salt);
    input.extend_from_slice(key.as_bytes());
    let hex = sha256::digest(input);
    let raw = hex.as_bytes();
    let mut out: u64 = 0;
    for index in 0..8 {
        let high = (raw[index * 2] as char).to_digit(16).unwrap_or(0) as u64;
        let low = (raw[index * 2 + 1] as char).to_digit(16).unwrap_or(0) as u64;
        out |= ((high << 4) | low) << (index * 8);
    }
    out
}

fn read_random(buffer: &mut [u8]) -> Result<()> {
    let mut file = std::fs::File::open("/dev/urandom").context("open /dev/urandom")?;
    file.read_exact(buffer).context("read /dev/urandom")
}
