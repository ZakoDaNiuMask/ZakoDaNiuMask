#ifndef __KSU_UAPI_SUPERCALL_H
#define __KSU_UAPI_SUPERCALL_H

#include <linux/ioctl.h>
#include <linux/types.h>

#include "uapi/app_profile.h"
#include "uapi/feature.h"
#include "uapi/selinux.h"

#define DEFINE_KSU_UAPI_CONST(type, name, val) \
    enum { name = (val) }; \
    static const type name##_RUST = (val);

#define KSU_FULL_VERSION_STRING 255

// 2: allowlist v4 root profile flags
// 3: scoped su-session driver fd
// 4: add KSU_GET_INFO_FLAG_BUNDLED
// 5: add EVENT_SERVICES with a start/skip result
static const __u32 KERNEL_SU_UAPI_VERSION = 5;

/* Magic numbers for reboot hook to install fd */
DEFINE_KSU_UAPI_CONST(__u32, KSU_INSTALL_MAGIC1, 0xDEADBEEF)
DEFINE_KSU_UAPI_CONST(__u32, KSU_INSTALL_MAGIC2, 0xCAFEBABE)

/* SuperKey authentication: reboot(KSU_INSTALL_MAGIC1, KSU_SUPERKEY_MAGIC2, 0, &cmd) */
DEFINE_KSU_UAPI_CONST(__u32, KSU_SUPERKEY_MAGIC2, 0xCAFE5555)
/* prctl(KSU_PRCTL_SUPERKEY_AUTH, &cmd, 0, 0, 0) */
#define KSU_PRCTL_SUPERKEY_AUTH 0x5A414B4F /* "ZAKO" */

struct ksu_superkey_auth_cmd {
    __u8 superkey[65]; /* Input: null-terminated password */
    __s32 result; /* Output: 0 on success, negative on failure */
    __s32 fd; /* Output: installed driver fd, or -1 */
};

struct ksu_superkey_status_cmd {
    __u8 is_configured; /* Output: a SuperKey is set in the kernel */
    __u8 is_authenticated; /* Output: the current process is authenticated */
    __u8 signature_bypass; /* Output: key-only (signature bypass) mode */
    __u8 reserved;
};

struct ksu_become_daemon_cmd {
    __u8 token[65]; /* Input: daemon token (null-terminated) */
};

DEFINE_KSU_UAPI_CONST(__u32, EVENT_POST_FS_DATA, 1)
DEFINE_KSU_UAPI_CONST(__u32, EVENT_BOOT_COMPLETED, 2)
DEFINE_KSU_UAPI_CONST(__u32, EVENT_MODULE_MOUNTED, 3)
DEFINE_KSU_UAPI_CONST(__u32, EVENT_SERVICES, 4)

DEFINE_KSU_UAPI_CONST(__u32, KSU_GET_INFO_FLAG_LKM, (1U << 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_GET_INFO_FLAG_MANAGER, (1U << 1))
DEFINE_KSU_UAPI_CONST(__u32, KSU_GET_INFO_FLAG_LATE_LOAD, (1U << 2))
DEFINE_KSU_UAPI_CONST(__u32, KSU_GET_INFO_FLAG_PR_BUILD, (1U << 3))
DEFINE_KSU_UAPI_CONST(__u32, KSU_GET_INFO_FLAG_BUNDLED, (1U << 4))

struct ksu_get_info_cmd {
    __u32 version; /* Output: KERNEL_SU_VERSION */
    __u32 flags; /* Output: KSU_GET_INFO_FLAG_* bits */
    __u32 features; /* Output: max feature ID supported */
    __u32 uapi_version; /* Output: KERNEL_SU_UAPI_VERSION */
};

struct ksu_get_info_legacy_cmd {
    __u32 version; /* Output: KERNEL_SU_VERSION */
    __u32 flags; /* Output: KSU_GET_INFO_FLAG_* bits */
    __u32 features; /* Output: max feature ID supported */
};

struct ksu_report_event_cmd {
    __u32 event; /* Input: EVENT_POST_FS_DATA, EVENT_BOOT_COMPLETED, etc. */
};

struct ksu_set_sepolicy_cmd {
    __u64 data_len; /* Input: bytes of serialized command payload */
    __aligned_u64 data; /* Input: pointer to serialized payload */
};

struct ksu_sepolicy_cmd_hdr {
    __u32 cmd; /* Input: command type, CMD_* */
    __u32 subcmd; /* Input: command subtype */
};
/*
 * After each ksu_sepolicy_cmd_hdr, command arguments are encoded sequentially as:
 * [u32 len][len bytes][\0], where len excludes the trailing '\0'.
 * len == 0 represents ALL.
 * Argument count is derived from cmd:
 * KSU_SEPOLICY_CMD_NORMAL_PERM=4, KSU_SEPOLICY_CMD_XPERM=5,
 * KSU_SEPOLICY_CMD_TYPE_STATE=1, KSU_SEPOLICY_CMD_TYPE=2,
 * KSU_SEPOLICY_CMD_TYPE_ATTR=2, KSU_SEPOLICY_CMD_ATTR=1,
 * KSU_SEPOLICY_CMD_TYPE_TRANSITION=5, KSU_SEPOLICY_CMD_TYPE_CHANGE=4,
 * KSU_SEPOLICY_CMD_GENFSCON=3.
 */

struct ksu_check_safemode_cmd {
    __u8 in_safe_mode; /* Output: true if in safe mode, false otherwise */
};

/* deprecated */
struct ksu_get_allow_list_cmd {
    __u32 uids[128]; /* Output: array of allowed/denied UIDs */
    __u32 count; /* Output: number of UIDs in array */
    __u8 allow; /* Input: true for allow list, false for deny list */
};

struct ksu_new_get_allow_list_cmd {
    __u16 count; /* Input / Output: number of UIDs in array */
    __u16 total_count; /* Output: total number of UIDs in requested list */
    __u32 uids[0]; /* Output: array of allowed/denied UIDs */
};

struct ksu_uid_granted_root_cmd {
    __u32 uid; /* Input: target UID to check */
    __u8 granted; /* Output: true if granted, false otherwise */
};

struct ksu_uid_should_umount_cmd {
    __u32 uid; /* Input: target UID to check */
    __u8 should_umount; /* Output: true if should umount, false otherwise */
};

struct ksu_get_manager_appid_cmd {
    __u32 appid; /* Output: manager app id */
};

struct ksu_get_app_profile_cmd {
    struct app_profile profile; /* Input/Output: app profile structure */
};

struct ksu_set_app_profile_cmd {
    struct app_profile profile; /* Input: app profile structure */
};

struct ksu_get_feature_cmd {
    __u32 feature_id; /* Input: feature ID (enum ksu_feature_id) */
    __u64 value; /* Output: feature value/state */
    __u8 supported; /* Output: true if feature is supported, false otherwise */
};

struct ksu_set_feature_cmd {
    __u32 feature_id; /* Input: feature ID (enum ksu_feature_id) */
    __u64 value; /* Input: feature value/state to set */
};

struct ksu_get_wrapper_fd_cmd {
    __u32 fd; /* Input: userspace fd */
    __u32 flags; /* Input: flags of userspace fd */
};

struct ksu_manage_mark_cmd {
    __u32 operation; /* Input: KSU_MARK_* */
    __s32 pid; /* Input: target pid (0 for all processes) */
    __u32 result; /* Output: for get operation - mark status or reg_count */
};

DEFINE_KSU_UAPI_CONST(__u32, KSU_MARK_GET, 1)
DEFINE_KSU_UAPI_CONST(__u32, KSU_MARK_MARK, 2)
DEFINE_KSU_UAPI_CONST(__u32, KSU_MARK_UNMARK, 3)
DEFINE_KSU_UAPI_CONST(__u32, KSU_MARK_REFRESH, 4)

struct ksu_nuke_ext4_sysfs_cmd {
    __aligned_u64 arg; /* Input: mnt pointer */
};

struct ksu_get_sulog_fd_cmd {
    __u32 flags; /* Input: reserved for future use, must be 0 */
};

struct ksu_manage_try_umount_cmd {
    __aligned_u64 arg; /* char ptr, this is the mountpoint */
    __u32 flags; /* this is the flag we use for it */
    // downstream: 107,200 = getsize old/new; 108,201 = getlist old/new
    __u8 mode; /* denotes what to do with it 0:wipe_list 1:add_to_list 2:delete_entry */
};

DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_WIPE, 0) /* ignore everything and wipe list */
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_ADD, 1) /* add entry (path + flags) */
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_DEL, 2) /* delete entry, strcmp */

// Downstream add mode
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_GETSIZE_LEGACY, 107) // get list size (legacy)
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_GETLIST_LEGACY, 108) // get list (legacy)
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_GETSIZE_NEW, 200) // get list size (new (with flags))
DEFINE_KSU_UAPI_CONST(__u8, KSU_UMOUNT_GETLIST_NEW, 201) // get list (new (with flags))

// Downstream supercall struct
struct ksu_get_full_version_cmd {
    char version_full[KSU_FULL_VERSION_STRING]; // Output: full version string
};

struct ksu_hook_type_cmd {
    char hook_type[32]; // Output: hook type string
};

struct ksu_enable_kpm_cmd {
    __u8 enabled; // Output: true if KPM is enabled
};

DEFINE_KSU_UAPI_CONST(__u8, DYNAMIC_MANAGER_OP_SET, 0)
DEFINE_KSU_UAPI_CONST(__u8, DYNAMIC_MANAGER_OP_GET, 1)
DEFINE_KSU_UAPI_CONST(__u8, DYNAMIC_MANAGER_OP_WIPE, 2)
DEFINE_KSU_UAPI_CONST(__u8, DYNAMIC_MANAGER_OP_SET_SYNCHRONOUS, 3)

struct ksu_dynamic_manager_cmd {
    __u8 operation;
    unsigned int size;
    __u8 hash[64];
};

struct ksu_manager_entry {
    __u32 uid;
    __u8 signature_index;
} __attribute__((packed));

struct ksu_get_managers_cmd {
    __u16 count; // Input / Output: number of managers in array
    __u16 total_count; // Output: total number of managers in requested list
    struct ksu_manager_entry managers[]; // Output: Array of active manager
} __attribute__((packed));

DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_LOAD, 1)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_UNLOAD, 2)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_NUM, 3)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_LIST, 4)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_INFO, 5)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_CONTROL, 6)
DEFINE_KSU_UAPI_CONST(__u8, KSU_KPM_VERSION, 7)

struct ksu_kpm_cmd {
    __u8 __user control_code;
    __aligned_u64 __user arg1;
    __aligned_u64 __user arg2;
    __aligned_u64 __user result_code;
} __attribute__((packed));

DEFINE_KSU_UAPI_CONST(__u8, KERNEL_PATCH_NOT_FOUND, 0)
DEFINE_KSU_UAPI_CONST(__u8, KERNEL_PATCH_ORIGINAL, 1)
DEFINE_KSU_UAPI_CONST(__u8, KERNEL_PATCH_KPN, 2)
DEFINE_KSU_UAPI_CONST(__u8, KERNEL_PATCH_SUKISU, 3)

struct ksu_get_kernel_patch_implement {
    __u8 type; // Output: Current Kernel Patch Implement
};

struct ksu_set_spoof_version_cmd {
    __u8 release[65]; /* Input: e.g., "5.10.115-android12-9-g00000000" */
    __u8 version[65]; /* Input: e.g., "#1 SMP PREEMPT Thu Jan 1 00:00:00 UTC 2026" */
};

struct ksu_set_spoof_cpu_cmd {
    __u32 cpu_index;  /* Target processor core index */
    __u32 midr;       /* Main ID Register payload */
    __u32 bogomips;   /* BogoMIPS performance timing metric */
    __u64 hwcap;      /* Main ELF Hardware Capabilities mask */
    __u64 hwcap2;     /* Auxiliary ELF Hardware Capabilities mask */
};

struct ksu_set_spoof_mem_cmd {
    __u64 total_ram_bytes; /* Target total memory size in bytes (0 to disable) */
    __u64 cma_total_bytes; /* Target total CMA size in bytes, can be 0 */
};

// Downstream supercall struct: fork feature toggles
struct ksu_extra_feature_cmd {
    __u64 value; /* In: 0/1 to set; Out: resulting state */
    __u8 query; /* In: 1 = query only, 0 = set */
};

/* i386 aligns __u64 to 4 bytes while every 64-bit arch aligns it to 8, and the
 * driver points .compat_ioctl at the same handler, so every 64-bit field in
 * the ptctl/uhook structs is force-aligned to keep one layout for both. */
#ifndef __aligned_s64
#define __aligned_s64 __s64 __attribute__((aligned(8)))
#endif

/* ---- ptctl: general process control / debug primitives (root only) ---- */
enum ksu_ptctl_op {
    /* A SHORT transfer is success, not failure: access_process_vm() stops at
     * the first page it cannot reach (ret < len). Total failure is -EIO. */
    KSU_PTCTL_PEEK          = 1,  /* read  task mem: pid, addr, len(<=64K), uptr(out) -> ret=bytes */
    KSU_PTCTL_POKE          = 2,  /* write task mem: pid, addr, len(<=64K), uptr(in)  -> ret=bytes */
    /* GETREGS/SETREGS transfer the USER register view: struct user_pt_regs on
     * arm64, struct pt_regs on x86_64. len = 0 means the whole user view. */
    KSU_PTCTL_GETREGS       = 3,  /* read user regs of tid: pid, uptr(out), len=0  -> ret=bytes */
    KSU_PTCTL_SETREGS       = 4,  /* write user regs of tid: pid, uptr(in), len=0  -> ret=bytes */
    KSU_PTCTL_INFO          = 5,  /* query task: pid -> arg1=tracer_pid arg2=tgid ret=1 if exists */
    /* Guard a tgid against signals injected via do_send_sig_info(). Table
     * holds 32 entries and has no exit hook: delete the guard when done. */
    KSU_PTCTL_KILLGUARD     = 6,  /* protect a tgid from lethal signals: pid, arg1(1=add,0=del) */
    KSU_PTCTL_SIGSEND       = 7,  /* send signal arg1 (1.._NSIG-1) to pid */
    KSU_PTCTL_DETACH_TRACER = 8,  /* force-detach pid from its ptracer (experimental) */
    /* Hold-breakpoint: pauses the hitting thread so it can be inspected.
     * NEVER point this at an address a KSU_IOCTL_UHOOK uprobe also covers. */
    KSU_PTCTL_HWBP_SET      = 9,  /* pid=tgid, addr (4-byte aligned) -> ret=threads armed, arg2=threads */
    KSU_PTCTL_HWBP_WAIT     = 10, /* block up to arg1 ms; on hit: uptr<-regs, arg2=tid, arg1=parked */
    KSU_PTCTL_HWBP_RELEASE  = 11, /* resume the currently-held thread; -ENOENT if none */
    KSU_PTCTL_HWBP_CLEAR    = 12, /* remove the bp -> ret=hits dropped since SET */
};

struct ksu_ptctl_cmd {
    __u32 op;              /* Input: enum ksu_ptctl_op */
    __s32 pid;             /* Input: target pid or tid */
    __aligned_u64 addr;    /* Input: target address (peek/poke) */
    __aligned_u64 len;     /* Input: byte length (peek/poke/regs) */
    __aligned_u64 uptr;    /* Input/Output: userspace buffer */
    __aligned_u64 arg1;    /* Input: op-specific (HWBP_WAIT also returns the parked flag) */
    __aligned_u64 arg2;    /* Output: op-specific */
    __aligned_s64 ret;     /* Output: op-specific result */
};

/* ---- uhook: kernel-mediated userspace instrumentation via uprobes ---- */
enum ksu_uhook_op {
    KSU_UHOOK_ADD   = 1, /* install a hook -> ret = hook id (>= 0), arg1 = live address spaces */
    KSU_UHOOK_DEL   = 2, /* remove hook `id` */
    KSU_UHOOK_CLEAR = 3, /* remove every hook -> ret = number removed */
    KSU_UHOOK_LIST  = 4, /* ret = number of active hooks; optional status into uptr(len) */
    KSU_UHOOK_READ  = 5, /* drain the capture ring into uptr(len); ret = bytes, arg1 = records */
};

enum ksu_uhook_site {
    KSU_UHOOK_ON_ENTRY = 0,
    KSU_UHOOK_ON_RET   = 1, /* control-flow actions are ON_RET only */
};

enum ksu_uhook_action {
    KSU_UHOOK_OBSERVE   = 0, /* record registers into the capture ring */
    KSU_UHOOK_SETREG    = 1, /* regs[act_reg] = act_val (index 0 at ON_RET forges return value) */
    KSU_UHOOK_FORCE_RET = 2, /* rejected (-EOPNOTSUPP); use SETREG at ON_RET */
    KSU_UHOOK_JUMP      = 3, /* pc = act_val (detour) -- ON_RET only */
    KSU_UHOOK_SKIP      = 4, /* pc += act_val -- ON_RET only */
    KSU_UHOOK_POKE      = 5, /* write ADD-supplied bytes to *(regs[act_reg]) + act_off */
};

enum ksu_uhook_cond {
    KSU_UHOOK_COND_NONE = 0,
    KSU_UHOOK_COND_REG  = 1, /* fire iff regs[cond_reg] <cmp> cond_val */
    KSU_UHOOK_COND_MEM  = 2, /* fire iff cond_len bytes at regs[cond_reg]+cond_off <cmp> cond_val */
};

enum ksu_uhook_cmp {
    KSU_UHOOK_EQ  = 0,
    KSU_UHOOK_NE  = 1,
    KSU_UHOOK_LT  = 2, /* unsigned */
    KSU_UHOOK_GT  = 3, /* unsigned */
    KSU_UHOOK_AND = 4, /* (value & cond_val) != 0 */
    KSU_UHOOK_SLT = 5, /* signed */
    KSU_UHOOK_SGT = 6, /* signed */
};

struct ksu_uhook_cmd {
    __u32 op;              /* Input: enum ksu_uhook_op */
    __u32 id;              /* Input: hook id (DEL); ADD returns the id via ret */
    __aligned_u64 path;    /* Input(ADD): user ptr to NUL-terminated file path */
    __aligned_u64 offset;  /* Input(ADD): FILE offset of the probed instruction */
    __u32 site;            /* Input(ADD): enum ksu_uhook_site */
    __s32 filter_tgid;     /* Input(ADD): owning process (tgid); MUST be > 0 */
    __s32 __pad0;          /* reserved, must be 0 */
    __u32 cond;            /* Input(ADD): enum ksu_uhook_cond */
    __u32 cond_reg;        /* Input(ADD): register index used by the condition */
    __u32 cond_cmp;        /* Input(ADD): enum ksu_uhook_cmp */
    __u32 cond_len;        /* Input(ADD): mem condition width: 1/2/4/8 */
    __aligned_s64 cond_off; /* Input(ADD): mem condition byte offset from *cond_reg */
    __aligned_u64 cond_val; /* Input(ADD): value to compare against */
    __u32 action;          /* Input(ADD): enum ksu_uhook_action */
    __u32 act_reg;         /* Input(ADD): SETREG/POKE register index */
    __aligned_s64 act_off; /* Input(ADD): POKE byte offset from *act_reg */
    __aligned_u64 act_val; /* Input(ADD): SETREG value / JUMP addr / SKIP byte count */
    __aligned_u64 uptr;    /* Input(ADD POKE)/Output(READ, LIST) */
    __aligned_u64 len;     /* Input: uptr byte length */
    __u32 cap_regs;        /* Input(ADD OBSERVE): leading registers to record; 0 = all 34 */
    __aligned_s64 ret;     /* Output: op result */
    __aligned_u64 arg1;    /* Output: op-specific */
    __aligned_u64 lost;    /* Output(READ, LIST): cumulative records dropped on ring overrun */
};

struct ksu_uhook_status {
    __u32 id;              /* hook id */
    __u32 site;            /* enum ksu_uhook_site */
    __u32 action;          /* enum ksu_uhook_action */
    __s32 filter_tgid;     /* scope as resolved at ADD time */
    __aligned_u64 offset;  /* file offset the hook was installed at */
    __aligned_u64 traps;   /* probed instruction executed */
    __aligned_u64 hits;    /* scope + condition passed, action ran */
    __aligned_u64 fails;   /* action/condition could not reach target memory */
};

struct ksu_uhook_record {
    __u32 id;             /* hook id that fired */
    __s32 tid;            /* thread id that hit the probe */
    __u64 ts_ns;          /* monotonic timestamp */
    __u64 regs[34];       /* x0..x30, sp, pc, pstate (first cap_regs are meaningful) */
};

/* IOCTL command definitions */
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GRANT_ROOT, _IOC(_IOC_NONE, 'K', 1, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_INFO, _IOR('K', 2, struct ksu_get_info_cmd))
// deprecated
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_INFO_LEGACY, _IOC(_IOC_READ, 'K', 2, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_REPORT_EVENT, _IOC(_IOC_WRITE, 'K', 3, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SET_SEPOLICY, _IOC(_IOC_READ | _IOC_WRITE, 'K', 4, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_CHECK_SAFEMODE, _IOC(_IOC_READ, 'K', 5, 0))
// deprecated
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_ALLOW_LIST, _IOC(_IOC_READ | _IOC_WRITE, 'K', 6, 0))
// deprecated
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_DENY_LIST, _IOC(_IOC_READ | _IOC_WRITE, 'K', 7, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_NEW_GET_ALLOW_LIST, _IOWR('K', 6, struct ksu_new_get_allow_list_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_NEW_GET_DENY_LIST, _IOWR('K', 7, struct ksu_new_get_allow_list_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_UID_GRANTED_ROOT, _IOC(_IOC_READ | _IOC_WRITE, 'K', 8, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_UID_SHOULD_UMOUNT, _IOC(_IOC_READ | _IOC_WRITE, 'K', 9, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_MANAGER_APPID, _IOC(_IOC_READ, 'K', 10, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_APP_PROFILE, _IOC(_IOC_READ | _IOC_WRITE, 'K', 11, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SET_APP_PROFILE, _IOC(_IOC_WRITE, 'K', 12, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_FEATURE, _IOC(_IOC_READ | _IOC_WRITE, 'K', 13, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SET_FEATURE, _IOC(_IOC_WRITE, 'K', 14, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_WRAPPER_FD, _IOC(_IOC_WRITE, 'K', 15, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_MANAGE_MARK, _IOC(_IOC_READ | _IOC_WRITE, 'K', 16, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_NUKE_EXT4_SYSFS, _IOC(_IOC_WRITE, 'K', 17, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_MANAGE_TRY_UMOUNT, _IOC(_IOC_WRITE, 'K', 18, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SET_INIT_PGRP, _IO('K', 19))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_SULOG_FD, _IOW('K', 20, struct ksu_get_sulog_fd_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_DISABLE_ESCAPE_TO_ROOT, _IO('K', 21))

// Downstream add IOCTL command definitions
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_FULL_VERSION, _IOC(_IOC_READ, 'K', 100, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_HOOK_TYPE, _IOC(_IOC_READ, 'K', 101, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_ENABLE_KPM, _IOC(_IOC_READ, 'K', 102, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_DYNAMIC_MANAGER, _IOC(_IOC_READ | _IOC_WRITE, 'K', 103, 0))
// 104 = old get_managers, deprecated
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_MANAGERS, _IOC(_IOC_READ | _IOC_WRITE, 'K', 105, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_GET_KERNEL_PATCH_IMPLEMENT, _IOC(_IOC_READ, 'K', 106, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SET_SPOOF_VERSION, _IOC(_IOC_WRITE, 'K', 104, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SET_SPOOF_CPU, _IOC(_IOC_WRITE, 'K', 107, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SET_SPOOF_MEM, _IOC(_IOC_WRITE, 'K', 108, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_PTCTL, _IOWR('K', 50, struct ksu_ptctl_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_UHOOK, _IOWR('K', 51, struct ksu_uhook_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_KPM, _IOC(_IOC_READ | _IOC_WRITE, 'K', 200, 0))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SUPERKEY_AUTH, _IOWR('K', 109, struct ksu_superkey_auth_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SUPERKEY_STATUS, _IOWR('K', 110, struct ksu_superkey_status_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_MOUNT_HIDE, _IOWR('K', 111, struct ksu_extra_feature_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SAMSUNG_COMPAT, _IOWR('K', 112, struct ksu_extra_feature_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_PTCTL_ENABLE, _IOWR('K', 113, struct ksu_extra_feature_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_UHOOK_ENABLE, _IOWR('K', 114, struct ksu_extra_feature_cmd))
DEFINE_KSU_UAPI_CONST(__u32, KSU_IOCTL_SG_SPOOF, _IOWR('K', 115, struct ksu_extra_feature_cmd))
#undef DEFINE_KSU_UAPI_CONST
#endif
