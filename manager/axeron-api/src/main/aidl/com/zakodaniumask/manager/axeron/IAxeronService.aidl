// SPDX-License-Identifier: GPL-3.0-or-later
package com.zakodaniumask.manager.axeron;

interface IAxeronService {
    int getVersion();

    int getUid();

    String exec(in String command);

    void exit();
}
