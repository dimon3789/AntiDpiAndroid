#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# Сборка нативного движка ByeDPI (ciadpi) под Android NDK (API 29+)
# Результат копируется в app/src/main/jniLibs/<ABI>/libciadpi.so
# Источник ByeDPI: https://github.com/hufrea/byedpi
# ============================================================================

API_LEVEL="${API_LEVEL:-29}"
ABIS="${ABIS:-arm64-v8a armeabi-v7a}"
WORK_DIR="${WORK_DIR:-$(pwd)/.build_byedpi}"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI_LIBS_DIR="${PROJECT_ROOT}/app/src/main/jniLibs"

# 1. Поиск ANDROID_NDK_HOME
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
    if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}/ndk" ]]; then
        LATEST_NDK="$(ls -1 "${ANDROID_HOME}/ndk" | sort -V | tail -n 1)"
        ANDROID_NDK_HOME="${ANDROID_HOME}/ndk/${LATEST_NDK}"
    else
        echo "Ошибка: задайте переменную ANDROID_NDK_HOME (или ANDROID_HOME с установленным NDK)." >&2
        exit 1
    fi
fi

# 2. Определение хост-платформы NDK toolchain
OS_NAME="$(uname -s | tr '[:upper:]' '[:lower:]')"
case "${OS_NAME}" in
    linux*)  HOST_TAG="linux-x86_64" ;;
    darwin*) HOST_TAG="darwin-x86_64" ;;
    *)       echo "Неподдерживаемая ОС хоста: ${OS_NAME}" >&2; exit 1 ;;
esac

TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/${HOST_TAG}"
if [[ ! -d "${TOOLCHAIN}" ]]; then
    echo "Ошибка: toolchain не найден по пути ${TOOLCHAIN}" >&2
    exit 1
fi

echo "Используется NDK: ${ANDROID_NDK_HOME}"
echo "API Level: ${API_LEVEL}, ABIs: ${ABIS}"

# 3. Клонирование или обновление hufrea/byedpi
mkdir -p "${WORK_DIR}"
SRC_DIR="${WORK_DIR}/byedpi"
if [[ ! -d "${SRC_DIR}/.git" ]]; then
    git clone --depth 1 https://github.com/hufrea/byedpi.git "${SRC_DIR}"
else
    git -C "${SRC_DIR}" pull --ff-only
fi

# 4. Кросс-компиляция под каждую архитектуру
for ABI in ${ABIS}; do
    case "${ABI}" in
        arm64-v8a)
            CC_BIN="${TOOLCHAIN}/bin/aarch64-linux-android${API_LEVEL}-clang"
            ;;
        armeabi-v7a)
            CC_BIN="${TOOLCHAIN}/bin/armv7a-linux-androideabi${API_LEVEL}-clang"
            ;;
        x86_64)
            CC_BIN="${TOOLCHAIN}/bin/x86_64-linux-android${API_LEVEL}-clang"
            ;;
        *)
            echo "Пропуск неизвестного ABI: ${ABI}" >&2
            continue
            ;;
    esac

    STRIP_BIN="${TOOLCHAIN}/bin/llvm-strip"
    echo "===> Сборка ciadpi для ${ABI}..."

    pushd "${SRC_DIR}" >/dev/null
    make clean >/dev/null 2>&1 || true

    # Флаги -fPIE -pie обязательны для исполняемых файлов на Android 5.0+ (и Android 10+)
    make CC="${CC_BIN}"          CFLAGS="-O2 -fPIE -D__ANDROID_API__=${API_LEVEL}"          LDFLAGS="-pie"

    OUT_DIR="${JNI_LIBS_DIR}/${ABI}"
    mkdir -p "${OUT_DIR}"
    cp -f ciadpi "${OUT_DIR}/libciadpi.so"
    "${STRIP_BIN}" "${OUT_DIR}/libciadpi.so"
    chmod 755 "${OUT_DIR}/libciadpi.so"
    popd >/dev/null

    echo "Готово: ${OUT_DIR}/libciadpi.so"
    file "${OUT_DIR}/libciadpi.so" || true
done

echo "Сборка завершена успешно."
