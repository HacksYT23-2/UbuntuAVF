UbuntuAVF

An Android app that boots a real Ubuntu 24.04 LTS virtual machine on-device using the Android Virtualization Framework (AVF) — the same underlying mechanism as Android 16's native Terminal app, which ships Debian instead.

Status: early / experimental. AVF hardware support (pKVM) has not yet been confirmed on the target device. See Known issues below.

What this is

Android 16 introduced a native Terminal app that boots a Debian VM via AVF. This project follows the same architecture but targets Ubuntu instead, built by forking the real AOSP source for that app rather than guessing at the config format.

UbuntuAVF (this app)
  └─ AVF VirtualMachineManager API
      └─ vm_config.json + kernel + initrd + root_part (Ubuntu rootfs)
          └─ crosvm / pKVM boots the guest
              └─ app connects over vsock for terminal I/O
Requirements
A device with genuine AVF/pKVM hardware support. Per AVF's own diagnostics, this generally means Pixel 7+ or Snapdragon 8 Gen 2+ class devices — pKVM requires the OEM to specifically build and ship a kernel with the EL2 hypervisor stub enabled, not just a fast-enough chip. The Android Emulator does not support this (no /dev/kvm on emulated ARM64) — testing must be done on physical hardware.
Android Studio (recent stable)
WSL2 (if developing on Windows) — needed only for building the rootfs image; the Android app itself builds fine natively on Windows
Repo layout
app/src/main/assets/
  vm_config.json       # AVF VM config (kernel/initrd/disk paths, memory, params)
  ubuntu-rootfs.img     # NOT committed — see "Getting the rootfs image" below
app/src/main/java/...   # App source (VM launch, vsock terminal bridge, UI)
Getting the rootfs image

The Ubuntu disk image is not in this repo. At ~3.5GB it exceeds GitHub's file-size limits, so it's fetched separately. Two options:

Build it yourself (see docs/building-the-rootfs.md if present, or the build steps below) and drop it into app/src/main/assets/ubuntu-rootfs.img for local testing.
Download-on-first-launch (the long-term plan, matching how the real Terminal app handles Debian) — not yet implemented in this app. See Roadmap below.
Building the rootfs image (WSL2)
bash
wget https://cloud-images.ubuntu.com/releases/noble/release/ubuntu-24.04-server-cloudimg-arm64.img
qemu-img convert -f qcow2 -O raw ubuntu-24.04-server-cloudimg-arm64.img ubuntu-rootfs.img
qemu-img resize ubuntu-rootfs.img +4G

sudo losetup -fP ubuntu-rootfs.img
sudo mkdir -p /mnt/ubuntu-rootfs
sudo mount /dev/loop0p1 /mnt/ubuntu-rootfs

# ARM64 image on an x86_64 host needs qemu-user-static for the chroot to work
sudo apt install -y qemu-user-static binfmt-support
sudo cp /usr/bin/qemu-aarch64-static /mnt/ubuntu-rootfs/usr/bin/

sudo chroot /mnt/ubuntu-rootfs /bin/bash
# inside the chroot:
passwd root
systemctl disable cloud-init cloud-init-local cloud-config cloud-final
exit

sudo umount /mnt/ubuntu-rootfs
sudo losetup -d /dev/loop0

Cloud-init is disabled deliberately — Ubuntu's cloud image expects a cloud metadata service that AVF doesn't provide, and leaving it enabled makes first boot hang while it times out searching for one.

vm_config.json
json
{
  "kernel": "kernel",
  "initrd": "initrd",
  "disks": [
    { "image": { "path": "ubuntu-rootfs.img" }, "writable": true }
  ],
  "protected": false,
  "platform_version": "1.0",
  "memory_mib": 4096,
  "cpu_topology": "match_host",
  "params": "rootfstype=ext4 rw console=hvc0"
}

Modeled on ConfigJson.kt from AOSP's packages/modules/Virtualization/android/TerminalApp — the real source for Android's Debian Terminal app, forked as the basis for this project instead of reverse-engineering the format from scratch.

Building & running
bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

Install to a physical device — see Requirements above regarding emulator limitations.

Known issues
AVF hardware support unconfirmed on target device. The app's built-in Diagnostics screen (Dashboard → AVF Framework Diagnostics) will show whether /dev/kvm/pKVM is actually available. If "AVF Service Supported" shows Missing on a real phone (not the emulator), that device's OEM likely hasn't enabled pKVM in their kernel build.
Rootfs image not committed (see above) — must be built locally for now.
Kernel/initrd sourcing still manual. AVF guests share a common virtio-enabled kernel across distros; this project currently expects kernel/initrd files already present alongside vm_config.json rather than fetching/building them automatically.
Roadmap
 Implement download-on-first-launch for the rootfs image (mirrors ImageArchive/InstalledImage from the AOSP Terminal source), so the image doesn't need to be pre-bundled or committed anywhere
 Host the customized (cloud-init-disabled) image on external storage (object storage / GitHub Releases) rather than requiring a local WSL2 build
 Confirm and document kernel/initrd sourcing (shared AVF kernel vs. per-distro)
 Arch Linux variant (ArchAVF), reusing the same kernel/initrd and app scaffolding with a different rootfs
Credit / references
Built by forking source from AOSP's packages/modules/Virtualization (android/TerminalApp), Apache 2.0 licensed.
AVF developer docs
Content

cat ImageArchive.kt /* * Copyright (C) 2024 The Android Open Source Project * * Licensed under the Apache License, Version 2.0 (the "License"); * you may not use this file except in compliance with the License. * You may obtain a copy of the License at * * http://www.apache.org/l

PASTED

cat InstalledImage.kt /* * Copyright (C) 2024 The Android Open Source Project * * Licensed under the Apache License, Version 2.0 (the "License"); * you may not use this file except in compliance with the License. * You may obtain a copy of the License at * * http://www.apache.org

PASTED

cat ConfigJson.kt /* * Copyright (C) 2024 The Android Open Source Project * * Licensed under the Apache License, Version 2.0 (the "License"); * you may not use this file except in compliance with the License. * You may obtain a copy of the License at * * http://www.apache.org/lic

PASTED
