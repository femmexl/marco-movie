"""Sync flutter/src to flutter/lib, bundle brand logo assets, and configure Apple/Windows native shells."""
from pathlib import Path
import re
import shutil
import struct
import subprocess
ROOT = Path(__file__).resolve().parent
REPO_ROOT = ROOT.parent
LOGO_MARK = REPO_ROOT / "android" / "src" / "main" / "res" / "drawable-nodpi" / "logo_mark.png"
OPAQUE_ICON = REPO_ROOT / "docs" / "images" / "app-icon-512.png"

def _png_dimensions(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if len(data) >= 24 and data[:8] == b"\x89PNG\r\n\x1a\n":
        w, h = struct.unpack(">II", data[16:24])
        if w > 0 and h > 0:
            return w, h
    return 1024, 1024

def _replace_xcassets_icons(appiconset_dir: Path, source_png: Path) -> None:
    if not appiconset_dir.exists() or not source_png.exists():
        return
    sips = shutil.which("sips")
    for png_file in appiconset_dir.glob("*.png"):
        w, h = _png_dimensions(png_file)
        shutil.copy2(source_png, png_file)
        if sips:
            subprocess.run(
                [sips, "-z", str(h), str(w), str(png_file)],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )

def _write_windows_ico(ico_path: Path, source_png: Path) -> None:
    if not ico_path.parent.exists() or not source_png.exists():
        return
    w, h = _png_dimensions(source_png)
    png_bytes = source_png.read_bytes()
    # Win32 ICO PNG entries MUST be <= 256x256; if source_png is larger (e.g. 512x512),
    # downscale to 256x256 PNG via PowerShell System.Drawing on Windows so GDI LoadIcon never fails.
    if (w > 256 or h > 256) and shutil.which("powershell"):
        tmp_png = ico_path.parent / "app_icon_256.png"
        ps_cmd = (
            "Add-Type -AssemblyName System.Drawing; "
            f"$src = [System.Drawing.Image]::FromFile('{source_png}'); "
            "$bmp = New-Object System.Drawing.Bitmap(256, 256); "
            "$g = [System.Drawing.Graphics]::FromImage($bmp); "
            "$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic; "
            "$g.DrawImage($src, 0, 0, 256, 256); "
            f"$bmp.Save('{tmp_png}', [System.Drawing.Imaging.ImageFormat]::Png); "
            "$g.Dispose(); $bmp.Dispose(); $src.Dispose()"
        )
        subprocess.run(
            ["powershell", "-NoProfile", "-Command", ps_cmd],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        if tmp_png.exists():
            png_bytes = tmp_png.read_bytes()
            tmp_png.unlink(missing_ok=True)
        else:
            return
    elif w > 256 or h > 256:
        return
    header = struct.pack("<HHH", 0, 1, 1)
    entry = struct.pack(
        "<BBBBHHII",
        0,  # 256px width
        0,  # 256px height
        0,
        0,
        1,
        32,
        len(png_bytes),
        22,
    )
    ico_path.write_bytes(header + entry + png_bytes)

def main() -> None:
    src = ROOT / "src"
    lib = ROOT / "lib"
    if src.exists():
        shutil.copytree(src, lib, dirs_exist_ok=True)
    # Bundle user's brand logo into flutter/assets/logo_mark.png
    assets_dir = ROOT / "assets"
    assets_dir.mkdir(parents=True, exist_ok=True)
    if LOGO_MARK.exists():
        shutil.copy2(LOGO_MARK, assets_dir / "logo_mark.png")
    widget_test = ROOT / "test" / "widget_test.dart"
    if widget_test.exists():
        widget_test.unlink()
    # macOS entitlements, deployment target, app icon, and native dark full-size window
    macos_runner = ROOT / "macos" / "Runner"
    if macos_runner.exists():
        _replace_xcassets_icons(
            macos_runner / "Assets.xcassets" / "AppIcon.appiconset",
            LOGO_MARK if LOGO_MARK.exists() else OPAQUE_ICON,
        )
        for ent in macos_runner.glob("*.entitlements"):
            text = ent.read_text(encoding="utf-8")
            if "com.apple.security.network.client" not in text:
                text = text.replace(
                    "</dict>",
                    "  <key>com.apple.security.network.client</key>\n  <true/>\n</dict>",
                )
            # Offline downloads are saved to ~/Downloads/Marco Movie
            if "com.apple.security.files.downloads.read-write" not in text:
                text = text.replace(
                    "</dict>",
                    "  <key>com.apple.security.files.downloads.read-write</key>\n  <true/>\n</dict>",
                )
            ent.write_text(text, encoding="utf-8")
        info_plist = macos_runner / "Info.plist"
        if info_plist.exists():
            text = info_plist.read_text(encoding="utf-8")
            text = text.replace("<string>marco_movie</string>", "<string>Marco Movie</string>")
            if "NSAppTransportSecurity" not in text:
                text = text.replace(
                    "</dict>",
                    "  <key>NSAppTransportSecurity</key>\n  <dict>\n    <key>NSAllowsArbitraryLoads</key>\n    <true/>\n  </dict>\n</dict>",
                    1,
                )
            info_plist.write_text(text, encoding="utf-8")
        window_swift = macos_runner / "MainFlutterWindow.swift"
        if window_swift.exists():
            swift_text = window_swift.read_text(encoding="utf-8")
            if "titlebarAppearsTransparent" not in swift_text:
                swift_text = swift_text.replace(
                    "super.awakeFromNib()",
                    "self.titleVisibility = .hidden\n"
                    "    self.titlebarAppearsTransparent = true\n"
                    "    self.styleMask.insert(.fullSizeContentView)\n"
                    "    self.appearance = NSAppearance(named: .darkAqua)\n"
                    "    self.minSize = NSSize(width: 900, height: 600)\n"
                    "    super.awakeFromNib()",
                )
                window_swift.write_text(swift_text, encoding="utf-8")
    macos_podfile = ROOT / "macos" / "Podfile"
    if macos_podfile.exists():
        text = macos_podfile.read_text(encoding="utf-8")
        text = re.sub(
            r"#?\s*platform\s+:osx\s*,\s*'[^']+'",
            "platform :osx, '11.0'",
            text,
        )
        macos_podfile.write_text(text, encoding="utf-8")
    macos_pbx = ROOT / "macos" / "Runner.xcodeproj" / "project.pbxproj"
    if macos_pbx.exists():
        text = macos_pbx.read_text(encoding="utf-8")
        text = re.sub(
            r"MACOSX_DEPLOYMENT_TARGET\s*=\s*10\.\d+;",
            "MACOSX_DEPLOYMENT_TARGET = 11.0;",
            text,
        )
        macos_pbx.write_text(text, encoding="utf-8")
    # iOS Info.plist, deployment target, and opaque AppIcon set
    ios_runner = ROOT / "ios" / "Runner"
    if ios_runner.exists():
        _replace_xcassets_icons(
            ios_runner / "Assets.xcassets" / "AppIcon.appiconset",
            OPAQUE_ICON if OPAQUE_ICON.exists() else LOGO_MARK,
        )
        ios_plist = ios_runner / "Info.plist"
        if ios_plist.exists():
            text = ios_plist.read_text(encoding="utf-8")
            text = text.replace("<string>Marco Movie</string>", "<string>Marco Movie</string>")
            text = text.replace("<string>marco_movie</string>", "<string>Marco Movie</string>")
            if "NSAppTransportSecurity" not in text:
                text = text.replace(
                    "</dict>",
                    "  <key>UIStatusBarStyle</key>\n"
                    "  <string>UIStatusBarStyleLightContent</string>\n"
                    "  <key>UIViewControllerBasedStatusBarAppearance</key>\n"
                    "  <false/>\n"
                    "  <key>NSAppTransportSecurity</key>\n"
                    "  <dict>\n"
                    "    <key>NSAllowsArbitraryLoads</key>\n"
                    "    <true/>\n"
                    "  </dict>\n"
                    "</dict>",
                    1,
                )
            # Expose offline downloads (Documents/Marco Movie) in the Files app
            if "UIFileSharingEnabled" not in text:
                text = text.replace(
                    "</dict>\n</plist>",
                    "  <key>UIFileSharingEnabled</key>\n"
                    "  <true/>\n"
                    "  <key>LSSupportsOpeningDocumentsInPlace</key>\n"
                    "  <true/>\n"
                    "</dict>\n</plist>",
                    1,
                )
            ios_plist.write_text(text, encoding="utf-8")
    ios_app_fw_plist = ROOT / "ios" / "Flutter" / "AppFrameworkInfo.plist"
    if ios_app_fw_plist.exists():
        text = ios_app_fw_plist.read_text(encoding="utf-8")
        text = re.sub(
            r"(<key>MinimumOSVersion</key>\s*<string>)[^<]+(</string>)",
            r"\g<1>14.0\g<2>",
            text,
        )
        ios_app_fw_plist.write_text(text, encoding="utf-8")
    for xcconfig_name in ("Release.xcconfig", "Debug.xcconfig", "Profile.xcconfig"):
        xcconfig = ROOT / "ios" / "Flutter" / xcconfig_name
        if xcconfig.exists():
            text = xcconfig.read_text(encoding="utf-8")
            if "no_fixup_chains" not in text:
                text = (
                    text.rstrip()
                    + "\nIPHONEOS_DEPLOYMENT_TARGET = 14.0\n"
                    + "OTHER_LDFLAGS = $(inherited) -Wl,-no_fixup_chains\n"
                    + "OTHER_CFLAGS = $(inherited) -fno-objc-msgsend-selector-stubs\n"
                )
                xcconfig.write_text(text, encoding="utf-8")
    ios_pbx = ROOT / "ios" / "Runner.xcodeproj" / "project.pbxproj"
    if ios_pbx.exists():
        text = ios_pbx.read_text(encoding="utf-8")
        text = re.sub(
            r"IPHONEOS_DEPLOYMENT_TARGET\s*=\s*[0-9.]+;",
            "IPHONEOS_DEPLOYMENT_TARGET = 14.0;",
            text,
        )
        ios_pbx.write_text(text, encoding="utf-8")
    ios_podfile = ROOT / "ios" / "Podfile"
    if ios_podfile.exists():
        text = ios_podfile.read_text(encoding="utf-8")
        text = re.sub(
            r"#?\s*platform\s+:ios\s*,\s*'[^']+'",
            "platform :ios, '14.0'",
            text,
        )
        if "no_fixup_chains" not in text and "flutter_additional_ios_build_settings(target)" in text:
            text = text.replace(
                "flutter_additional_ios_build_settings(target)",
                "flutter_additional_ios_build_settings(target)\n"
                "    target.build_configurations.each do |config|\n"
                "      config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '14.0'\n"
                "      config.build_settings['OTHER_LDFLAGS'] = '$(inherited) -Wl,-no_fixup_chains'\n"
                "      config.build_settings['OTHER_CFLAGS'] = '$(inherited) -fno-objc-msgsend-selector-stubs'\n"
                "    end",
            )
        ios_podfile.write_text(text, encoding="utf-8")
    # Windows icon, working directory, immediate window show, and title
    win_runner = ROOT / "windows" / "runner"
    win_ico = win_runner / "resources" / "app_icon.ico"
    if win_ico.parent.exists():
        _write_windows_ico(
            win_ico,
            OPAQUE_ICON if OPAQUE_ICON.exists() else LOGO_MARK,
        )
    win_main = win_runner / "main.cpp"
    if win_main.exists():
        text = win_main.read_text(encoding="utf-8")
        text = text.replace('L"marco_movie"', 'L"Marco Movie"')
        if "SetCurrentDirectoryW" not in text and "wWinMain(" in text:
            init_hook = (
                "  wchar_t exe_path[MAX_PATH];\n"
                "  if (::GetModuleFileNameW(nullptr, exe_path, MAX_PATH) > 0) {\n"
                "    wchar_t* last_slash = wcsrchr(exe_path, L'\\\\');\n"
                "    if (last_slash) {\n"
                "      *last_slash = L'\\0';\n"
                "      ::SetCurrentDirectoryW(exe_path);\n"
                "    }\n"
                "  }\n"
            )
            text = text.replace(
                "  // Attach to console when present",
                init_hook + "  // Attach to console when present",
                1,
            )
        win_main.write_text(text, encoding="utf-8")
    win_fw = win_runner / "flutter_window.cpp"
    if win_fw.exists():
        text = win_fw.read_text(encoding="utf-8")
        if "this->Show();" in text and "// Immediate Show on Windows" not in text:
            text = text.replace(
                "SetChildContent(flutter_controller_->view()->GetNativeWindow());",
                "SetChildContent(flutter_controller_->view()->GetNativeWindow());\n"
                "  // Immediate Show on Windows so the window always appears right away\n"
                "  this->Show();",
                1,
            )
            win_fw.write_text(text, encoding="utf-8")

if __name__ == "__main__":
    main()
