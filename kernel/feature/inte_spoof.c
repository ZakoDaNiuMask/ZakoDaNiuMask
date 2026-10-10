// SPDX-License-Identifier: GPL-2.0
/*
 * inte spoof: neutralises Oplus's inte.ko kernel integrity checker.
 *
 * inte (vendor/oplus/kernel/secureguard/.../oplus_kernel_security_check.c,
 * built as the standalone DDK module "inte") hashes sys_call_table hourly
 * against an early snapshot and kretprobes load_module to hash every loaded
 * .ko against a vendor-supplied baseline, then exposes the findings on
 * /proc/inte_ko and /proc/inte_systbl for the vendor userspace to upload. On
 * a rooted device those two checks light up immediately.
 *
 * Note: inte is a separate component from oplus_secure_guard_new (the
 * root/exec/cap/sepolicy/socket guards); this feature only covers inte.
 *
 * When enabled this feature:
 *   1. adds "inte" to the module-load blocklist, so it cannot be (re)loaded
 *      through init_module/finit_module;
 *   2. creates /proc/inte_ko, /proc/inte_systbl and /proc/inte_status with
 *      the same modes and owner as the originals, reading back empty and
 *      swallowing writes -- exactly what a clean, non-rooted device shows.
 * The caller (ksud) must rmmod inte first; creating a node whose name is
 * still held returns -EBUSY.
 *
 * Disabling removes the fake nodes and unblocks the module; inte is *not*
 * re-inserted, it comes back on the next boot.
 */

#include "feature/inte_spoof.h"

#include <linux/cred.h>
#include <linux/fs.h>
#include <linux/mutex.h>
#include <linux/proc_fs.h>
#include <linux/seq_file.h>
#include <linux/string.h>
#include <linux/uaccess.h>

#include "feature/module_load_filter.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_identity.h"

#define INTE_BLOCK_TARGET "inte"
#define INTE_PROC_KO "inte_ko"
#define INTE_PROC_SYSTBL "inte_systbl"
#define INTE_PROC_STATUS "inte_status"

static bool inte_spoof_enabled __read_mostly = false;
static DEFINE_MUTEX(inte_spoof_mutex);
static bool inte_spoof_nodes_created;

static int inte_spoof_proc_show_empty(struct seq_file *m, void *v)
{
    /* A clean device reports no events. */
    return 0;
}

static int inte_spoof_proc_open(struct inode *inode, struct file *file)
{
    return single_open(file, inte_spoof_proc_show_empty, NULL);
}

static ssize_t inte_spoof_proc_write(struct file *file, const char __user *buffer, size_t count, loff_t *ppos)
{
    /* Swallow vendor writes (baseline hashes, event packets, status flags). */
    return count;
}

static const struct proc_ops inte_spoof_proc_fops_rw = {
    .proc_open = inte_spoof_proc_open,
    .proc_read = seq_read,
    .proc_lseek = seq_lseek,
    .proc_release = single_release,
    .proc_write = inte_spoof_proc_write,
};

static const struct proc_ops inte_spoof_proc_fops_wo = {
    .proc_open = simple_open,
    .proc_write = inte_spoof_proc_write,
};

static void inte_spoof_block_module(void)
{
    size_t len = strnlen(ksu_block_modules, sizeof(ksu_block_modules));
    size_t target = strlen(INTE_BLOCK_TARGET);

    if (len + 1 + target + 1 > sizeof(ksu_block_modules)) {
        pr_err("inte_spoof: blocklist full, cannot add %s\n", INTE_BLOCK_TARGET);
        return;
    }
    if (len && ksu_block_modules[len - 1] != ',')
        ksu_block_modules[len++] = ',';
    memcpy(ksu_block_modules + len, INTE_BLOCK_TARGET, target);
    ksu_block_modules[len + target] = '\0';
}

static void inte_spoof_unblock_module(void)
{
    char *start = ksu_block_modules;
    size_t list_len = strnlen(ksu_block_modules, sizeof(ksu_block_modules));
    size_t target = strlen(INTE_BLOCK_TARGET);
    char *cursor = start;
    char *end = start + list_len;
    char *hit = NULL;

    while (cursor < end) {
        char *sep = memchr(cursor, ',', end - cursor);
        size_t entry_len = sep ? (size_t)(sep - cursor) : (size_t)(end - cursor);

        if (entry_len == target && memcmp(cursor, INTE_BLOCK_TARGET, target) == 0) {
            hit = cursor;
            /* Include the trailing comma when it is not the last entry. */
            if (sep)
                entry_len++;
            memmove(hit, hit + entry_len, end - (hit + entry_len));
            list_len -= entry_len;
            ksu_block_modules[list_len] = '\0';
            end = start + list_len;
            cursor = start;
            hit = NULL;
            continue;
        }

        if (!sep)
            break;
        cursor = sep + 1;
    }
}

static int inte_spoof_create_nodes(void)
{
    struct proc_dir_entry *ko;
    struct proc_dir_entry *systbl;
    struct proc_dir_entry *status;

    ko = proc_create(INTE_PROC_KO, 0664, NULL, &inte_spoof_proc_fops_rw);
    if (!ko)
        return -EBUSY;
    proc_set_user(ko, KUIDT_INIT(0), KGIDT_INIT(0));

    systbl = proc_create(INTE_PROC_SYSTBL, 0664, NULL, &inte_spoof_proc_fops_rw);
    if (!systbl) {
        remove_proc_entry(INTE_PROC_KO, NULL);
        return -EBUSY;
    }
    proc_set_user(systbl, KUIDT_INIT(0), KGIDT_INIT(0));

    status = proc_create(INTE_PROC_STATUS, 0660, NULL, &inte_spoof_proc_fops_wo);
    if (!status) {
        remove_proc_entry(INTE_PROC_SYSTBL, NULL);
        remove_proc_entry(INTE_PROC_KO, NULL);
        return -EBUSY;
    }
    proc_set_user(status, KUIDT_INIT(0), KGIDT_INIT(0));

    inte_spoof_nodes_created = true;
    pr_info("inte_spoof: /proc/%s /proc/%s /proc/%s registered\n", INTE_PROC_KO, INTE_PROC_SYSTBL, INTE_PROC_STATUS);
    return 0;
}

static void inte_spoof_remove_nodes(void)
{
    if (!inte_spoof_nodes_created)
        return;
    remove_proc_entry(INTE_PROC_KO, NULL);
    remove_proc_entry(INTE_PROC_SYSTBL, NULL);
    remove_proc_entry(INTE_PROC_STATUS, NULL);
    inte_spoof_nodes_created = false;
    pr_info("inte_spoof: fake nodes removed\n");
}

static int inte_spoof_feature_set(u64 value)
{
    int ret = 0;

    mutex_lock(&inte_spoof_mutex);
    if (value) {
        inte_spoof_block_module();
        ret = inte_spoof_create_nodes();
        if (ret) {
            pr_err("inte_spoof: enable failed (%d), is inte still loaded?\n", ret);
            inte_spoof_unblock_module();
        } else {
            inte_spoof_enabled = true;
            pr_info("inte_spoof: enabled, inte blocked\n");
        }
    } else {
        inte_spoof_remove_nodes();
        inte_spoof_unblock_module();
        inte_spoof_enabled = false;
        pr_info("inte_spoof: disabled\n");
    }
    mutex_unlock(&inte_spoof_mutex);
    return ret;
}

static int inte_spoof_feature_get(u64 *value)
{
    mutex_lock(&inte_spoof_mutex);
    *value = inte_spoof_enabled ? 1 : 0;
    mutex_unlock(&inte_spoof_mutex);
    return 0;
}

int ksu_inte_spoof_feature_get(u64 *value)
{
    return inte_spoof_feature_get(value);
}

int ksu_inte_spoof_feature_set(u64 value)
{
    return inte_spoof_feature_set(value);
}

void __init ksu_inte_spoof_init(void)
{
    pr_info("inte_spoof: init (disabled by default)\n");
}
