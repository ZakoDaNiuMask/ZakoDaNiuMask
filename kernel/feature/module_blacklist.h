#ifndef __KSU_MODULE_BLACKLIST_H
#define __KSU_MODULE_BLACKLIST_H

#include <linux/types.h>

void ksu_module_blacklist_init(const char *modules);
void ksu_module_blacklist_exit();

/* Runtime updates of the managed blacklist. */
int ksu_module_blacklist_add(const char *name);
int ksu_module_blacklist_remove(const char *name);
bool ksu_module_blacklist_contains(const char *name);

#endif
