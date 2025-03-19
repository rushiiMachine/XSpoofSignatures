# XSpoofSignatures

Xposed module to spoof package signatures.

## Features

- Supports Android >= 1.5 (Cupcake)
- Compatible with the standardized spoofing
  mechanic ([microG](https://github.com/microg/GmsCore/tree/a787b52ccc56b2e197bf38e1229bb4206538cd12/patches))
- Allow any package to spoof their signature through manifest properties
	- Allow bypassing GMS checks
- Spoofing gated behind a permission

## Install

1. Use [Magisk](https://github.com/topjohnwu/Magisk) to root your device
2. Enable Zygisk
3. Install [LSPosed](https://github.com/LSPosed/LSPosed) or the updated [LSPosed fork](https://github.com/mywalkb/LSPosed_mod)
4. Install XSpoofSignatures from [here](https://github.com/rushiiMachine/XSpoofSignatures/releases/latest)
5. Enable it in LSPosed
6. Verify that signature spoofing works via [Signature Spoofing Checker](https://f-droid.org/en/packages/lanchon.sigspoof.checker)
7. You can now use apps that require signature spoofing (like microG)

## Usage (developers)

XSpoofSignatures supports two methods of spoofing:

- Sole signer [DEFAULT]: spoof to be the only signer on a package
- First signer: spoof to be the first signer on a package (multi signers)
	- Note: this does not work for all signer checks such as GMS

Each package **must** declare and request (at runtime) the `android.permission.FAKE_PACKAGE_SIGNATURE` permission.\
If the permission is not granted then no spoofing will occur.

This module will check for two [`<meta-data>`](https://developer.android.com/guide/topics/manifest/meta-data-element) tags for each package that wants
to spoof it's own signature:

- `fake-signature` -> The certificate fingerprint in hex
- `fake-signature-only` (optional, default `true`) -> Refer to "Sole signer" above
