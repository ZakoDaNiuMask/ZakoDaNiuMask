// SPDX-License-Identifier: GPL-2.0
/*
 * Module load blacklist.
 *
 * Two mechanisms are combined here:
 *
 *  1. The kernel's own `module_blacklist` parameter is rewritten so that the
 *     kernel module core refuses to load the listed modules. This is the
 *     upstream BakaSU approach: it needs no syscall hook at all and lets the
 *     kernel do the filtering.
 *
 *  2. When a syscall hook is available (tracepoint hook), init_module and
 *     finit_module are hooked so that an -EPERM produced by the blacklist
 *     itself is reported as success. Android's libmodprobe treats a non-zero
 *     return as a hard error, while ksuinit uses __NR_init_module, so the
 *     blocked module has to look like it loaded successfully.
 *
 * The list can be changed at runtime through ksu_module_blacklist_add() and
 * ksu_module_blacklist_remove(), which re-allocate the kernel-visible string
 * and swap the module_blacklist pointer. This keeps the blacklist in sync
 * with features that block a module on demand (see inte_spoof).
 *
 * Ownership:
 *   - `orig_blacklist` is the kernel's original string. It is captured once,
 *     at init, and never reassigned. The kernel owns that memory.
 *   - `blacklist` is the string we installed into the kernel. We own it and
 *     free it when replacing it or on exit.
 *   - `ksu_blacklist` is our own copy of the names we manage. The kernel's
 *     own entries are not part of it.
 */

#include <linux/init.h>
#include <linux/mutex.h>
#include <linux/slab.h>
#include <linux/string.h>

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
#include "hook/syscall_hook.h"
#endif
#include "feature/module_blacklist.h"
#include "infra/symbol_resolver.h"
#include "klog.h" // IWYU pragma: keep

#if defined(KSU_COMPAT_HAS_INSMOD_HOOK) || defined(CONFIG_KSU_TRACEPOINT_HOOK)
/* The string we installed into the kernel; we own it. */
static char *blacklist = NULL;
/* The kernel's original string; the kernel owns it, we only remember it. */
static char *orig_blacklist = NULL;
static char **module_blacklist = NULL;

/* The names we manage, without the kernel's own entries. We own it. */
static char *ksu_blacklist = NULL;

/* Lock protecting ksu_blacklist and the pointer swap. */
static DEFINE_MUTEX(blacklist_lock);

// workaround https://cs.android.com/android/platform/superproject/+/android-latest-release:system/core/libmodprobe/utils.cpp;l=73
// userspace will throw error when ret != 0
// Android using __NR_finit_module, so we should hook this syscall
//
// https://github.com/tiann/KernelSU/blob/8948025de525cbf1ee03a895505a0a79ec283a7f/userspace/ksuinit/src/lib.rs#L365
// https://github.com/bytecodealliance/rustix/blob/287214b889865d8e1406a0ee71cc409b6f6191c8/src/system.rs#L265
// https://github.com/bytecodealliance/rustix/blob/1e2954a46ecffa6b30426ad7b1833b226530dcf4f/src/backend/libc/system/syscalls.rs#L125
// ksuinit using __NR_init_module, so we also need hook this syscall

// this just for hook check, in fact we can directly inline for perf opt, but..
void ksu_handle_init_module(int *result)
{
    if (*result == -EPERM)
        *result = 0;
}

void ksu_handle_finit_module(int *result)
{
    if (*result == -EPERM)
        *result = 0;
}

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
static long (*orig_sys_init_module)(const struct pt_regs *regs);
static long ksu_sys_init_module(const struct pt_regs *regs)
{
    int ret = orig_sys_init_module(regs);

    ksu_handle_init_module(&ret);

    return ret;
}

static long (*orig_sys_finit_module)(const struct pt_regs *regs);
static long ksu_sys_finit_module(const struct pt_regs *regs)
{
    int ret = orig_sys_finit_module(regs);

    ksu_handle_finit_module(&ret);

    return ret;
}
#endif

/* Join two comma separated name lists. The caller owns the result. */
static char *join_names(const char *a, const char *b)
{
    size_t a_len = a ? strlen(a) : 0;
    size_t b_len = b ? strlen(b) : 0;

    if (!a_len && !b_len)
        return kstrdup("", GFP_KERNEL);
    if (!a_len)
        return kstrdup(b, GFP_KERNEL);
    if (!b_len)
        return kstrdup(a, GFP_KERNEL);

    return kasprintf(GFP_KERNEL, "%s,%s", a, b);
}

/* Does the comma separated list contain the given name? */
static bool list_contains(const char *list, const char *name)
{
    size_t name_len = strlen(name);
    const char *cursor = list;
    const char *end;

    if (!list || !list[0])
        return false;

    end = list + strlen(list);
    while (cursor < end) {
        const char *separator = memchr(cursor, ',', end - cursor);
        size_t entry_len = separator ? (size_t)(separator - cursor) : (size_t)(end - cursor);

        if (entry_len == name_len && !memcmp(cursor, name, name_len)) {
            return true;
        }

        if (!separator)
            break;
        cursor = separator + 1;
    }

    return false;
}

/*
 * Install `list` as the kernel-visible blacklist, keeping whatever the
 * kernel originally had. The caller must hold blacklist_lock.
 *
 * The kernel's own entries are taken from orig_blacklist, not from the
 * current *module_blacklist: after the first install the kernel points at
 * our own buffer, so merging with it would append the managed list to
 * itself on every update.
 *
 * Returns 0 on success.
 */
static int apply_blacklist(const char *list)
{
    char *merged;
    char *new_blacklist;

    if (!module_blacklist) {
        pr_warn("module_blacklist: kernel symbol not found, skipping blocked modules\n");
        return -ENOENT;
    }

    merged = join_names(orig_blacklist, list);
    if (!merged) {
        pr_err("module_blacklist: cannot allocate blacklist\n");
        return -ENOMEM;
    }

    new_blacklist = merged;

    /* Free the buffer we installed before, it is ours. */
    if (blacklist)
        kfree(blacklist);

    blacklist = new_blacklist;
    *module_blacklist = blacklist;

    pr_info("module_blacklist: blocked modules: %s\n", blacklist);
    return 0;
}

void __init ksu_module_blacklist_init(const char *modules)
{
    const char *extra = "";
    char *initial;

#ifndef MODULE
    extra = modules[0] ? ",kernelsu" : "kernelsu";
#endif
    if (!modules[0] && !extra[0])
        return;

    module_blacklist = (char **)find_kernel_symbol_exact("module_blacklist");
    if (!module_blacklist) {
        pr_warn("module_blacklist: kernel symbol not found, skipping blocked modules\n");
        return;
    }

    /* Remember the kernel's original string; the kernel owns it. */
    orig_blacklist = (char *)*module_blacklist;

    /* Keep our own copy so runtime updates can rebuild the list. */
    initial = kasprintf(GFP_KERNEL, "%s%s", modules, extra);
    if (!initial) {
        pr_err("module_blacklist: cannot allocate blacklist\n");
        return;
    }
    ksu_blacklist = initial;

    if (apply_blacklist(ksu_blacklist) != 0) {
        kfree(ksu_blacklist);
        ksu_blacklist = NULL;
        return;
    }

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
    ksu_syscall_table_hook(__NR_init_module, ksu_sys_init_module, &orig_sys_init_module);
    ksu_syscall_table_hook(__NR_finit_module, ksu_sys_finit_module, &orig_sys_finit_module);
    pr_info("module_blacklist: syscall hooked\n");
#endif
}

/*
 * Add a module name to the managed blacklist at runtime. The kernel's own
 * blacklist entries are preserved.
 */
int ksu_module_blacklist_add(const char *name)
{
    char *updated;
    int ret;

    if (!name || !name[0]) {
        return -EINVAL;
    }

    mutex_lock(&blacklist_lock);

    if (list_contains(ksu_blacklist, name)) {
        mutex_unlock(&blacklist_lock);
        return 0;
    }

    updated = join_names(ksu_blacklist, name);
    if (!updated) {
        mutex_unlock(&blacklist_lock);
        pr_err("module_blacklist: cannot add %s to blacklist\n", name);
        return -ENOMEM;
    }

    kfree(ksu_blacklist);
    ksu_blacklist = updated;

    ret = apply_blacklist(ksu_blacklist);

    mutex_unlock(&blacklist_lock);
    return ret;
}

/*
 * Remove a module name from the managed blacklist at runtime.
 */
int ksu_module_blacklist_remove(const char *name)
{
    char *updated;
    char *cursor;
    char *write;
    char *end;
    size_t name_len;
    int ret;

    if (!name || !name[0]) {
        return -EINVAL;
    }

    mutex_lock(&blacklist_lock);

    if (!list_contains(ksu_blacklist, name)) {
        mutex_unlock(&blacklist_lock);
        return 0;
    }

    name_len = strlen(name);
    updated = kstrdup(ksu_blacklist ? ksu_blacklist : "", GFP_KERNEL);
    if (!updated) {
        mutex_unlock(&blacklist_lock);
        pr_err("module_blacklist: cannot remove %s from blacklist\n", name);
        return -ENOMEM;
    }

    /* Rebuild the list without the entry. */
    cursor = updated;
    write = updated;
    end = updated + strlen(updated);
    while (cursor < end) {
        char *separator = memchr(cursor, ',', end - cursor);
        size_t entry_len = separator ? (size_t)(separator - cursor) : (size_t)(end - cursor);

        if (!(entry_len == name_len && !memcmp(cursor, name, name_len))) {
            if (write != updated) {
                *write++ = ',';
            }
            memmove(write, cursor, entry_len);
            write += entry_len;
        }

        if (!separator)
            break;
        cursor = separator + 1;
    }
    *write = '\0';

    kfree(ksu_blacklist);
    ksu_blacklist = updated;

    ret = apply_blacklist(ksu_blacklist);

    mutex_unlock(&blacklist_lock);
    return ret;
}

/*
 * Test whether a module name is on the managed blacklist. Used by features
 * that need to know the current state.
 */
bool ksu_module_blacklist_contains(const char *name)
{
    bool found;

    if (!name || !name[0]) {
        return false;
    }

    mutex_lock(&blacklist_lock);
    found = list_contains(ksu_blacklist, name);
    mutex_unlock(&blacklist_lock);

    return found;
}

void __exit ksu_module_blacklist_exit()
{
    const char *existing;

    if (module_blacklist == NULL) {
        // that's mean no modules got block
        return;
    }

#ifdef CONFIG_KSU_TRACEPOINT_HOOK
    ksu_syscall_table_unhook(__NR_init_module);
    ksu_syscall_table_unhook(__NR_finit_module);

    pr_info("module_blacklist: syscall unhooked!\n");
#endif

    if (blacklist == NULL) {
        pr_warn("module_blacklist: blacklist ptr not init, skip kfree and revert changes!\n");
        return;
    }

    existing = *module_blacklist;

    if (existing != blacklist) {
        pr_warn("module_blacklist: looks module_blacklist changed by other, skip kfree and revert changes!\n");
        return;
    }

    *module_blacklist = orig_blacklist;
    kfree(blacklist); // we own this buffer
    blacklist = NULL;

    kfree(ksu_blacklist);
    ksu_blacklist = NULL;

    pr_info("module_blacklist: blacklist reverted and memory freed!\n");
}
#else
void __init ksu_module_blacklist_init(const char *modules)
{
    // nothing to do
}

void __exit ksu_module_blacklist_exit()
{
    // nothing to do
}

int ksu_module_blacklist_add(const char *name)
{
    return -EOPNOTSUPP;
}

int ksu_module_blacklist_remove(const char *name)
{
    return -EOPNOTSUPP;
}

bool ksu_module_blacklist_contains(const char *name)
{
    return false;
}
#endif
