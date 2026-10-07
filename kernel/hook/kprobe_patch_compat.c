#include <linux/types.h>

#include "hook/kprobe_patch_compat.h"
#include "infra/samsung_compat.h"

bool ksu_kprobe_text_patch_unsafe(void)
{
    if (!ksu_samsung_compat_enabled())
        return false;
    return ksu_samsung_uh_present() || ksu_samsung_defex_build_present();
}
