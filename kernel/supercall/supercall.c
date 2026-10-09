#include <linux/anon_inodes.h>
#include <linux/err.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/kprobes.h>
#include <linux/pid.h>
#include <linux/slab.h>
#include <linux/syscalls.h>
#include <linux/uaccess.h>
#include <linux/version.h>

#ifdef CONFIG_KSU_SUSFS
#include <linux/namei.h>
#include <linux/susfs.h>
#endif // #ifdef CONFIG_KSU_SUSFS

#include "compat/kernel_compat.h"
#include "uapi/supercall.h"
#include "supercall/internal.h"
#include "arch.h"
#include "hook/syscall_hook.h"
#include "hook/kprobe_patch_compat.h"
#include "klog.h" // IWYU pragma: keep

#ifdef CONFIG_KSU_SUPERKEY
#include "manager/superkey.h"
#endif

#define KSU_DRIVER_PERMISSION_SU_SESSION (1UL << 0)

struct ksu_driver_context {
    unsigned long permissions;
};

static int anon_ksu_release(struct inode *inode, struct file *filp)
{
    kfree(filp->private_data);
    pr_info("ksu fd released\n");
    return 0;
}

static long anon_ksu_ioctl(struct file *filp, unsigned int cmd, unsigned long arg)
{
    return ksu_supercall_handle_ioctl(filp, cmd, (void __user *)arg);
}

static const struct file_operations anon_ksu_fops = {
    .owner = THIS_MODULE,
    .unlocked_ioctl = anon_ksu_ioctl,
    .compat_ioctl = anon_ksu_ioctl,
    .release = anon_ksu_release,
};

static int ksu_install_fd_with_permissions(unsigned int fd_flags, unsigned long permissions)
{
    struct ksu_driver_context *context;
    struct file *filp;
    const char *name;
    int fd;

    // alloc context
    context = kzalloc(sizeof(*context), GFP_KERNEL);
    if (!context)
        return -ENOMEM;

    context->permissions = permissions;
    name = permissions & KSU_DRIVER_PERMISSION_SU_SESSION ? "[ksu_driver_su]" : "[ksu_driver]";

    // Get unused fd
    fd = get_unused_fd_flags(fd_flags);
    if (fd < 0) {
        pr_err("%s: failed to get unused fd\n", __func__);
        kfree(context);
        return fd;
    }

    // Create anonymous inode file
    filp = anon_inode_getfile(name, &anon_ksu_fops, context, O_RDWR);
    if (IS_ERR(filp)) {
        pr_err("%s: failed to create anon inode file\n", __func__);
        put_unused_fd(fd);
        kfree(context);
        return PTR_ERR(filp);
    }

    // Install fd
    fd_install(fd, filp);

    pr_info("ksu fd installed: %d, name: %s, for pid %d\n", fd, name, current->pid);

    return fd;
}

int ksu_install_fd(void)
{
    return ksu_install_fd_with_permissions(O_CLOEXEC, 0);
}

int ksu_install_su_fd(void)
{
    // This descriptor must be installed after the exec into ksud.
    return ksu_install_fd_with_permissions(O_CLOEXEC, KSU_DRIVER_PERMISSION_SU_SESSION);
}

bool ksu_is_su_session_fd(const struct file *filp)
{
    const struct ksu_driver_context *context = filp->private_data;

    return context && (context->permissions & KSU_DRIVER_PERMISSION_SU_SESSION);
}

#ifdef CONFIG_KSU_TOOLKIT_SUPPORT
extern int ksu_try_handle_toolkit_cmd(int magic2, unsigned int cmd, void __user **arg);
#endif

#ifdef CONFIG_KSU_SUSFS
extern int ksu_handle_susfs_cmd(unsigned int cmd, void __user **arg);
#endif

#ifdef CONFIG_KSU_SUPERKEY
/*
 * Shared SuperKey authentication: copies the command from userspace, verifies
 * the password, grants manager identity and installs the driver fd. Used by the
 * reboot handshake, the prctl TSR hook and the ioctl path.
 */
void ksu_superkey_finish(struct ksu_superkey_auth_cmd __user *cmd_user)
{
    struct ksu_superkey_auth_cmd cmd;
    int fd = -1;
    int result = -EACCES;

    if (!cmd_user)
        return;

    if (copy_from_user(&cmd, cmd_user, sizeof(cmd))) {
        pr_err("superkey auth: copy_from_user failed\n");
        return;
    }
    cmd.superkey[sizeof(cmd.superkey) - 1] = '\0';

    if (verify_superkey((const char *)cmd.superkey)) {
        uid_t uid = ksu_get_uid_t(current_uid());
        superkey_on_auth_success(uid);

        fd = ksu_install_fd();
        if (fd >= 0) {
            result = 0;
            pr_info("superkey auth: fd %d installed for uid %d\n", fd, uid);
        } else {
            result = fd;
            pr_err("superkey auth: failed to install fd: %d\n", fd);
        }
    } else {
        // Silent fail - do not reveal KSU existence.
        superkey_on_auth_fail();
        return;
    }

    cmd.result = result;
    cmd.fd = fd;
    if (copy_to_user(cmd_user, &cmd, sizeof(cmd))) {
        pr_err("superkey auth: copy_to_user failed\n");
        if (fd >= 0)
            ksu_close_fd(fd);
    }
}
#endif // #ifdef CONFIG_KSU_SUPERKEY

// downstream: make sure to pass arg as reference, this can allow us to extend things.
int ksu_handle_sys_reboot(int magic1, int magic2, unsigned int cmd, void __user **arg)
{
    if (magic1 != KSU_INSTALL_MAGIC1)
        return -EINVAL;

#ifdef CONFIG_KSU_DEBUG
    pr_info("sys_reboot: intercepted call! magic: 0x%x id: %d\n", magic1, magic2);
#endif

    // Check if this is a request to install KSU fd
    if (magic2 == KSU_INSTALL_MAGIC2) {
        int fd = ksu_install_fd();
        pr_info("[%d] install ksu fd: %d\n", current->pid, fd);

        if (copy_to_user((int __user *)*arg, &fd, sizeof(fd))) {
            pr_err("install ksu fd reply err\n");
            ksu_close_fd(fd);
        }
        return 0;
    }

#ifdef CONFIG_KSU_SUPERKEY
    // SuperKey authentication + fd install
    if (magic2 == KSU_SUPERKEY_MAGIC2) {
        ksu_superkey_finish((struct ksu_superkey_auth_cmd __user *)*arg);
        return 0;
    }
#endif

    // extensions

#if !defined(CONFIG_KSU_TOOLKIT_SUPPORT) && !defined(CONFIG_KSU_SUSFS)
    return 0;
#endif

    // other sys_reboot extensions are fully require uid 0,
    // so let's check it before
    if (ksu_get_uid_t(current_uid()) != 0)
        return 0;

#ifdef CONFIG_KSU_TOOLKIT_SUPPORT
    if (ksu_try_handle_toolkit_cmd(magic2, cmd, arg))
        return 0;
#endif

#ifdef CONFIG_KSU_SUSFS
    // If magic2 is susfs and current process is root
    if (magic2 == SUSFS_MAGIC) {
        return ksu_handle_susfs_cmd(cmd, arg);
    }
#endif
    return 0;
}

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
// Reboot hook for installing fd
static int reboot_handler_pre(struct kprobe *p, struct pt_regs *regs)
{
    struct pt_regs *real_regs = PT_REAL_REGS(regs);
    int magic1 = (int)PT_REGS_SYSCALL_PARM1(real_regs);
    int magic2 = (int)PT_REGS_PARM2(real_regs);
    int cmd = (int)PT_REGS_PARM3(real_regs);
    void __user **arg = (void __user **)&PT_REGS_SYSCALL_PARM4(real_regs);

    ksu_handle_sys_reboot(magic1, magic2, cmd, arg);
    return 0;
}

static struct kprobe reboot_kp = {
    .symbol_name = REBOOT_SYMBOL,
    .pre_handler = reboot_handler_pre,
};

static bool reboot_kp_registered;

/*
 * Compat install handshake for a kernel where reboot_kp above is unsafe to
 * trigger (ksu_kprobe_text_patch_unsafe(), hook/kprobe_patch_compat.h) --
 * confirmed on Samsung RKP/KDP/DEFEX hardware to crash exactly the way
 * syscall_regfunc's kretprobe does: the BRK write lands and verifies clean,
 * but the CPU still faults fetching it, before reboot_handler_pre ever runs.
 *
 * Keeps reboot(2) as the install-handshake syscall exactly as userspace
 * already calls it -- the difference is *how* the hook goes in. Overwriting
 * sys_call_table[__NR_reboot]'s pointer is a data write in a table,
 * redirecting to code that already exists and already executes fine (this
 * module's own), never injecting a single new instruction into anything.
 */
static syscall_fn_t real_reboot_fn;
static bool reboot_table_hooked;

static long ksu_reboot_table_replacement(const struct pt_regs *regs)
{
    int magic1 = (int)PT_REGS_PARM1(regs);
    int magic2 = (int)PT_REGS_PARM2(regs);

    if (magic1 == (int)KSU_INSTALL_MAGIC1 && magic2 == (int)KSU_INSTALL_MAGIC2) {
        unsigned long arg4 = (unsigned long)PT_REGS_SYSCALL_PARM4(regs);
        int fd = ksu_install_fd();

        pr_info("[%d] install ksu fd (table-hook compat): %d\n", current->pid, fd);
        if (copy_to_user((int __user *)arg4, &fd, sizeof(fd))) {
            pr_err("install ksu fd reply err (table-hook compat)\n");
            ksu_close_fd(fd);
        }
        return 0;
    }

#ifdef CONFIG_KSU_SUPERKEY
    if (magic1 == (int)KSU_INSTALL_MAGIC1 && magic2 == (int)KSU_SUPERKEY_MAGIC2) {
        unsigned long arg4 = (unsigned long)PT_REGS_SYSCALL_PARM4(regs);
        ksu_superkey_finish((struct ksu_superkey_auth_cmd __user *)arg4);
        return 0;
    }
#endif

    return real_reboot_fn(regs);
}
#endif

#ifdef CONFIG_KSU_SUPERKEY
static bool prctl_hook_registered;

// prctl(2) interception for SuperKey authentication. Registered only when a
// SuperKey is configured; the reboot handshake and the ioctl remain available.
static long __nocfi ksu_hook_prctl(int orig_nr, const struct pt_regs *regs)
{
    int option = (int)PT_REGS_PARM1(regs);
    unsigned long arg2 = (unsigned long)PT_REGS_PARM2(regs);

    if (option == KSU_PRCTL_SUPERKEY_AUTH)
        ksu_superkey_finish((struct ksu_superkey_auth_cmd __user *)arg2);

    return ksu_syscall_table[orig_nr](regs);
}
#endif // #ifdef CONFIG_KSU_SUPERKEY

void __init ksu_supercalls_init(void)
{
    int rc;

#ifdef CONFIG_KSU_SUPERKEY
    if (superkey_is_set()) {
        if (ksu_register_syscall_hook(__NR_prctl, ksu_hook_prctl) == 0) {
            prctl_hook_registered = true;
            pr_info("superkey: prctl TSR hook registered\n");
        } else {
            pr_err("superkey: prctl TSR hook registration failed\n");
        }
    } else {
        pr_info("superkey: signature-only mode, prctl hook not registered\n");
    }
#endif

    ksu_supercall_dump_commands();

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
    if (ksu_kprobe_text_patch_unsafe() && ksu_syscall_table) {
        ksu_syscall_table_hook(__NR_reboot, (syscall_fn_t)ksu_reboot_table_replacement, &real_reboot_fn);
        pr_info("reboot table-hook compat installed (reboot kprobe unsafe on this kernel)\n");
        reboot_table_hooked = true;
        return;
    }

    rc = register_kprobe(&reboot_kp);
    if (rc) {
        pr_err("reboot kprobe failed: %d\n", rc);
        return;
    }
    pr_info("reboot kprobe registered successfully\n");
    reboot_kp_registered = true;
#endif
}

void __exit ksu_supercalls_exit(void)
{
#ifdef CONFIG_KSU_TRACEPOINT_HOOK
    if (reboot_table_hooked)
        ksu_syscall_table_unhook(__NR_reboot);
    if (reboot_kp_registered)
        unregister_kprobe(&reboot_kp);
#endif
#ifdef CONFIG_KSU_SUPERKEY
    if (prctl_hook_registered) {
        ksu_unregister_syscall_hook(__NR_prctl);
        prctl_hook_registered = false;
    }
#endif
    ksu_supercall_cleanup_state();
}
