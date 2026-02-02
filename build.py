#!/usr/bin/env python3

import subprocess
import sys
import os
import argparse

def main():
    """編譯 UiAgentService Android 應用（主 APK 和/或測試 APK）"""
    
    # 解析命令行参数
    parser = argparse.ArgumentParser(description='編譯 UiAgentService APK')
    parser.add_argument('--main-only', action='store_true', 
                       help='只編譯主 APK')
    parser.add_argument('--test-only', action='store_true', 
                       help='只編譯測試 APK（Instrumentation）')
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

        # 編譯主 APK
        if compile_main:
            print("編譯主 APK...")
            result_main = subprocess.run(['./gradlew', 'assembleDebug'], check=True)

        # 編譯測試 APK（Instrumentation）
        if compile_test:
            print("編譯測試 APK (Instrumentation)...")
            result_test = subprocess.run(['./gradlew', 'assembleDebugAndroidTest'], check=True)

        print("=" * 50)
        print("編譯成功！")
        if compile_main:
            print("主 APK: app/build/outputs/apk/debug/app-debug.apk")
        if compile_test:
            print("測試 APK: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")

    except subprocess.CalledProcessError as e:
        print("=" * 50)
        print(f"編譯失敗：退出碼 {e.returncode}")
        sys.exit(1)
    except FileNotFoundError:
        print("錯誤：找不到 gradlew 腳本。請確保在 UiAgentService 目錄中。")
        sys.exit(1)

if __name__ == "__main__":
    main()