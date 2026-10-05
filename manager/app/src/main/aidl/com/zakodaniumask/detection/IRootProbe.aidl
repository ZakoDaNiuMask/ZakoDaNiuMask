// IRootProbe.aidl
package com.zakodaniumask.detection;

interface IRootProbe {
    /** Runs the preliminary SU/root probes in the isolated process and returns a JSON report. */
    String scan();
}
