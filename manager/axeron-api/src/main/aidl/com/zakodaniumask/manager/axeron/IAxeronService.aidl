// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.axeron;

interface IAxeronService {
    int getVersion();

    int getUid();

    String exec(in String command);

    /** Fire-and-forget shell command (no waiting, no output capture). */
    void execDetached(in String command);

    /** Writes [data] to [path] as the privileged user. Returns true on success. */
    boolean writeFile(in String path, in byte[] data);

    /** Reads [path] as the privileged user. Returns null when unreadable. */
    byte[] readFile(in String path);

    boolean fileExists(in String path);

    void exit();
}
