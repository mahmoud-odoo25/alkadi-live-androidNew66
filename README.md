# Al-Kadi Live Barcode

Dedicated white-labeled Android launcher for the Odoo Barcode module.

## Purpose

This package is not a general Odoo WebView wrapper. It is designed for handheld Android scanner devices that should open the Odoo Barcode module directly.

## Features

- App name: Al-Kadi Live Barcode
- App and watermark logo: Al-Kadi Live logo merged with a barcode icon
- Direct Barcode-module launch by default
- Default action target: `stock_barcode.stock_barcode_action_main_menu`
- Optional exact Barcode URL / action override from the app settings screen
- Barcode-only mode to hide general Odoo navigation where possible
- WebView camera permission handling for Odoo's browser-based barcode scanner
- Hardware scanner friendly: the WebView is focused and the barcode screen input is focused after load
- Blue Al-Kadi Android UI theme, while preserving the original red/blue logo artwork
- GitHub Actions workflow included for debug APK build

## Important Odoo-side control

For real handheld-only access, configure the Odoo user/security groups so the handheld user only has the operational access required for Barcode workflows. Android-side hiding is a convenience layer, not a replacement for Odoo permissions.

## GitHub Actions output

The workflow uploads the APK artifact as:

`al-kadi-live-barcode-debug-apk`
