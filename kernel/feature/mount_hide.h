#ifndef __KSU_H_MOUNT_HIDE
#define __KSU_H_MOUNT_HIDE

#include <linux/types.h>

void ksu_mount_hide_init(void);
void ksu_mount_hide_exit(void);

/* Downstream toggle (KSU_IOCTL_MOUNT_HIDE, see uapi/supercall.h). */
int ksu_mount_hide_feature_get(u64 *value);
int ksu_mount_hide_feature_set(u64 value);

#endif // __KSU_H_MOUNT_HIDE
