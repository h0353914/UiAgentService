#!/usr/bin/env python3

import subprocess
import sys
import os

def main():
    """編譯 UiAgentService Android 應用"""
    try:
        # 確保在正確的目錄中
        script_dir = os.path.dirname(os.path.abspath(__file__))
        os.chdir(script_dir)

        print("開始編譯 UiAgentService...")
        print("=" * 50)

        # 運行 ./gradlew build，實時顯示輸出
        result = subprocess.run(['./gradlew', 'build'], check=True)

        print("=" * 50)
        print("編譯成功！")

    except subprocess.CalledProcessError as e:
        print("=" * 50)
        print(f"編譯失敗：退出碼 {e.returncode}")
        sys.exit(1)
    except FileNotFoundError:
        print("錯誤：找不到 gradlew 腳本。請確保在 UiAgentService 目錄中。")
        sys.exit(1)

if __name__ == "__main__":
    main()