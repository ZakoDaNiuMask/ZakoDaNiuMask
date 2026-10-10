/* SPDX-License-Identifier: GPL-2.0 */
#ifndef __KSU_H_INTE_SPOOF
#define __KSU_H_INTE_SPOOF

#include <linux/types.h>

/* Neutralises the Oplus inte.ko kernel integrity checker (see the source). */

void ksu_inte_spoof_init(void);

/* Downstream toggle (KSU_IOCTL_INTE_SPOOF, see uapi/supercall.h). */
int ksu_inte_spoof_feature_get(u64 *value);
int ksu_inte_spoof_feature_set(u64 value);

#endif
