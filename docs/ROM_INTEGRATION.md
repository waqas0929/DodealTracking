# Privileged ROM integration (Moto G57)

The same APK adapts at runtime: `PrivilegeDetector` checks `FLAG_SYSTEM` and `AppContainer` selects
`PrivilegedRomStrategy` automatically when the app is on the system image, otherwise
`StandardApkStrategy`. No other module changes between the two builds.

## Baking the agent into the ROM

1. Place the APK as a privileged system app:
   `/system/priv-app/DodealDeviceAgent/DodealDeviceAgent.apk`
   (or `/product/priv-app/...`, depending on the ROM's partitioning).

2. Allowlist its privileged permissions (Android P+ requires this for priv-app):
   add `etc/permissions/privapp-permissions-dodeal.xml` to the system image:
   ```xml
   <permissions>
     <privapp-permissions package="com.dodeal.deviceagent">
       <permission name="android.permission.READ_PRIVILEGED_PHONE_STATE"/>
       <permission name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
       <permission name="android.permission.FOREGROUND_SERVICE_LOCATION"/>
     </privapp-permissions>
   </permissions>
   ```

3. Grant location at build time (so no runtime prompt) via the ROM's
   `default-permissions` / `DefaultPermissionGrantPolicy`, or preinstall + preset grants.

4. Exempt from battery optimisation / Doze by adding the package to the ROM's power allowlist
   (e.g. `config_powerSaveWhitelist` / `sysconfig allow-in-power-save`).

5. (Optional) Expose a ROM build id the agent reports as ROM Version:
   set a system property `ro.dodeal.rom.version=<build>` — `DeviceInfoProvider` reads it.

## What flips on automatically in ROM mode

- `canRunPrivileged()` = true, `canAutoStartTracking()` = true.
- Location tracking + telemetry start silently at boot (`BootCompletedReceiver`) and on process start
  (`DodealAgentApp`) — no Android 12+ background-start restriction.
- Real hardware serial via `READ_PRIVILEGED_PHONE_STATE` (`Build.getSerial()` succeeds).
- No battery-optimisation prompts; the ROM guarantees persistence.

## Signing

Sign the APK with the platform key only if you need `signatureOrSystem` permissions; the permissions
above work from priv-app + allowlist without the platform signature.
