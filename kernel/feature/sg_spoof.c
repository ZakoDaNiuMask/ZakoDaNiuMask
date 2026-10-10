// SPDX-License-Identifier: GPL-2.0
/*
 * SecureGuard spoof: neutralises Oplus's inte.ko kernel integrity checker.
 *
 * inte (vendor/oplus/kernel/secureguard/.../oplus_kernel_security_check.c)
 * hashes sys_call_table hourly against an early snapshot and kretprobes
 * load_module to hash every loaded .ko against a vendor-supplied baseline,
 * then exposes the findings on /proc/inte_ko and /proc/inte_systbl for the
 * vendor userspace to upload. On a rooted device those two checks light up
 * immediately.
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

#include "feature/sg_spoof.h"

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

#define SG_BLOCK_TARGET "inte"
#define SG_PROC_INTE_KO "inte_ko"
#define SG_PROC_INTE_SYSTBL "inte_systbl"
#define SG_PROC_INTE_STATUS "inte_status"

static bool sg_enabled __read_mostly = false;
static DEFINE_MUTEX(sg_mutex);
static bool sg_nodes_created;

static int sg_proc_show_empty(struct seq_file *m, void *v)
{
    /* A clean device reports no events. */
    return 0;
}

static int sg_proc_open(struct inode *inode, struct file *file)
{
    return single_open(file, sg_proc_show_empty, NULL);
}

static ssize_t sg_proc_write(struct file *file, const char __user *buffer, size_t count, loff_t *ppos)
{
    /* Swallow vendor writes (baseline hashes, event packets, status flags). */
    return count;
}

static const struct proc_ops sg_proc_fops_rw = {
    .proc_open = sg_proc_open,
    .proc_read = seq_read,
    .proc_lseek = seq_lseek,
    .proc_release = single_release,
    .proc_write = sg_proc_write,
};

static const struct proc_ops sg_proc_fops_wo = {
    .proc_open = simple_open,
    .proc_write = sg_proc_write,
};

static void sg_block_module(void)
{
    size_t len = strnlen(ksu_block_modules, sizeof(ksu_block_modules));
    size_t target = strlen(SG_BLOCK_TARGET);

    if (len + 1 + target + 1 > sizeof(ksu_block_modules)) {
        pr_err("sg_spoof: blocklist full, cannot add %s\n", SG_BLOCK_TARGET);
        return;
    }
    if (len && ksu_block_modules[len - 1] != ',')
        ksu_block_modules[len++] = ',';
    memcpy(ksu_block_modules + len, SG_BLOCK_TARGET, target);
    ksu_block_modules[len + target] = '\0';
}

static void sg_unblock_module(void)
{
    char *start = ksu_block_modules;
    size_t list_len = strnlen(ksu_block_modules, sizeof(ksu_block_modules));
    size_t target = strlen(SG_BLOCK_TARGET);
    char *cursor = start;
    char *end = start + list_len;
    char *hit = NULL;

    while (cursor < end) {
        char *sep = memchr(cursor, ',', end - cursor);
        size_t entry_len = sep ? (size_t)(sep - cursor) : (size_t)(end - cursor);

        if (entry_len == target && memcmp(cursor, SG_BLOCK_TARGET, target) == 0) {
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

static int sg_create_nodes(void)
{
    struct proc_dir_entry *ko;
    struct proc_dir_entry *systbl;
    struct proc_dir_entry *status;

    ko = proc_create(SG_PROC_INTE_KO, 0664, NULL, &sg_proc_fops_rw);
    if (!ko)
        return -EBUSY;
    proc_set_user(ko, KUIDT_INIT(0), KGIDT_INIT(0));

    systbl = proc_create(SG_PROC_INTE_SYSTBL, 0664, NULL, &sg_proc_fops_rw);
    if (!systbl) {
        remove_proc_entry(SG_PROC_INTE_KO, NULL);
        return -EBUSY;
    }
    proc_set_user(systbl, KUIDT_INIT(0), KGIDT_INIT(0));

    status = proc_create(SG_PROC_INTE_STATUS, 0660, NULL, &sg_proc_fops_wo);
    if (!status) {
        remove_proc_entry(SG_PROC_INTE_SYSTBL, NULL);
        remove_proc_entry(SG_PROC_INTE_KO, NULL);
        return -EBUSY;
    }
    proc_set_user(status, KUIDT_INIT(0), KGIDT_INIT(0));

    sg_nodes_created = true;
    pr_info("sg_spoof: /proc/%s /proc/%s /proc/%s registered\n", SG_PROC_INTE_KO, SG_PROC_INTE_SYSTBL,
            SG_PROC_INTE_STATUS);
    return 0;
}

static void sg_remove_nodes(void)
{
    if (!sg_nodes_created)
        return;
    remove_proc_entry(SG_PROC_INTE_KO, NULL);
    remove_proc_entry(SG_PROC_INTE_SYSTBL, NULL);
    remove_proc_entry(SG_PROC_INTE_STATUS, NULL);
    sg_nodes_created = false;
    pr_info("sg_spoof: fake nodes removed\n");
}

static int sg_spoof_feature_set(u64 value)
{
    int ret = 0;

    mutex_lock(&sg_mutex);
    if (value) {
        sg_block_module();
        ret = sg_create_nodes();
        if (ret) {
            pr_err("sg_spoof: enable failed (%d), is inte still loaded?\n", ret);
            sg_unblock_module();
        } else {
            sg_enabled = true;
            pr_info("sg_spoof: enabled, inte blocked\n");
        }
    } else {
        sg_remove_nodes();
        sg_unblock_module();
        sg_enabled = false;
        pr_info("sg_spoof: disabled\n");
    }
    mutex_unlock(&sg_mutex);
    return ret;
}

static int sg_spoof_feature_get(u64 *value)
{
    mutex_lock(&sg_mutex);
    *value = sg_enabled ? 1 : 0;
    mutex_unlock(&sg_mutex);
    return 0;
}

int ksu_sg_spoof_feature_get(u64 *value)
{
    return sg_spoof_feature_get(value);
}

int ksu_sg_spoof_feature_set(u64 value)
{
    return sg_spoof_feature_set(value);
}

void __init ksu_sg_spoof_init(void)
{
    pr_info("sg_spoof: init (disabled by default)\n");
}
