#!/usr/bin/env python3

import subprocess
import sys
import os
import argparse
from pathlib import Path

# 添加上層目錄到 sys.path 以導入 adb.py
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))

from SemcCameraUI.tools_Common.adb import Adb  # noqa: E402

def main():
    """編譯 UiAgentService Android 應用（主 APK 與 UiAutomation 測試 APK）"""
    
    # 解析命令行参数
    parser = argparse.ArgumentParser(description='編譯 UiAgentService APK')
    parser.add_argument('--main-only', action='store_true', 
                       help='只編譯主 APK')
    parser.add_argument('--test-only', action='store_true', 
                       help='只編譯測試 APK（Instrumentation）')
    parser.add_argument('--no-install', action='store_true',
                       help='只編譯，不安裝 APK')
    parser.add_argument('-s', '--serial', type=str, default=None,
                       help='指定設備序號')
    args = parser.parse_args()
    
    
    # 确定编译目标
    compile_main = not args.test_only
    compile_test = not args.main_only
    
    try:
        # 確保在正確的目錄中
        script_dir = os.path.dirname(os.path.abspath(__file__))
        os.chdir(script_dir)

        print("開始編譯 UiAgentService...")
        print("=" * 50)

        # 編譯主 APK（無障礙服務）
        if compile_main:
            print("編譯主 APK（無障礙服務）...")
            subprocess.run(['./gradlew', ':app:assembleDebug'], check=True)

        # 編譯測試 APK（UiAutomation Instrumentation）
        if compile_test:
            print("編譯測試 APK（UiAutomation Instrumentation）...")
            subprocess.run(['./gradlew', ':uiautomation:assembleDebug'], check=True)
            subprocess.run(['./gradlew', ':uiautomation:assembleDebugAndroidTest'], check=True)

        print("=" * 50)
        print("編譯成功！")
        
        apk_files = []
        if compile_main:
            print("主 APK: app/build/outputs/apk/debug/app-debug.apk")
            apk_files.append(("app/build/outputs/apk/debug/app-debug.apk", "主 APK（無障礙服務）"))
        if compile_test:
            print("主 APK（UiAutomation 目標）: uiautomation/build/outputs/apk/debug/uiautomation-debug.apk")
            print("測試 APK: uiautomation/build/outputs/apk/androidTest/debug/uiautomation-debug-androidTest.apk")
            apk_files.append(("uiautomation/build/outputs/apk/debug/uiautomation-debug.apk", "UiAutomation 主 APK"))
            apk_files.append(("uiautomation/build/outputs/apk/androidTest/debug/uiautomation-debug-androidTest.apk", "Instrumentation 測試 APK"))

        # 安裝 APK
        if not args.no_install and apk_files:
            print("=" * 50)
            print("開始安裝 APK...")
            adb = Adb(serial=args.serial)
            
            for apk_path, apk_name in apk_files:
                if not os.path.exists(apk_path):
                    print(f"⚠️  警告：找不到 {apk_path}，跳過安裝")
                    continue
                
                print(f"安裝 {apk_name}...")
                try:
                    adb.run(["install", "-r", apk_path], timeout=120)
                    print(f"✓ {apk_name} 安裝成功")
                except RuntimeError as e:
                    print(f"✗ {apk_name} 安裝失敗：{e}")
                    sys.exit(1)
            
            print("=" * 50)
            print("✓ 所有 APK 安裝成功！")

    except subprocess.CalledProcessError as e:
        print("=" * 50)
        print(f"編譯失敗：退出碼 {e.returncode}")
        sys.exit(1)
    except FileNotFoundError:
        print("錯誤：找不到 gradlew 腳本。請確保在 UiAgentService 目錄中。")
        sys.exit(1)

if __name__ == "__main__":
    main()