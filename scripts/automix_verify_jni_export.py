#!/usr/bin/env python3
"""Bounded little-endian ELF dynsym check for the newly required JNI entry.
This detects old APK libraries; it is not an Android load/playback test.
"""
import struct

SYMBOL = b'Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_selectResolvedPairV2'
SOURCE_SYMBOL = b'Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_selectMusicKitSourcePairV1'
SCHEDULE_SYMBOL = b'Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_compileMusicKitScheduleV1'
OWNER_SYMBOLS = tuple(b"Java_com_lmg_vk_engine_automix_nativecore_NativePcmOwnerIngress_" + name for name in (
    b"nativeProtocol",
    b"nativeCreate",
    b"nativeStage",
    b"nativeCommit",
    b"nativeAbort",
    b"nativePush",
    b"nativeRead",
    b"nativeStats",
    b"nativeDestroy",
))
ABI_IDENTITIES = {'armeabi-v7a': (1, 40), 'arm64-v8a': (2, 183), 'x86': (1, 3)}

def verify_jni_export(data: bytes, elf_class: int, machine: int, symbol: bytes = SYMBOL) -> None:
    def need(ok: bool) -> None:
        if not ok:
            raise ValueError('Invalid or stale AutoMix JNI ELF')
    def integer(fmt: str, offset: int) -> int:
        need(offset >= 0 and offset + struct.calcsize(fmt) <= len(data))
        return struct.unpack_from('<' + fmt, data, offset)[0]
    need(symbol in (SYMBOL, SOURCE_SYMBOL, SCHEDULE_SYMBOL) + OWNER_SYMBOLS)
    need(len(data) >= (64 if elf_class == 2 else 52) and data[:4] == b'\x7fELF')
    need(elf_class in (1, 2) and data[4] == elf_class and data[5] == 1 and data[6] == 1)
    need(integer('H', 16) == 3 and integer('H', 18) == machine)
    wide = elf_class == 2
    offset = integer('Q' if wide else 'I', 40 if wide else 32)
    stride = integer('H', 58 if wide else 46)
    count = integer('H', 60 if wide else 48)
    need(0 < count <= 8192 and stride == (64 if wide else 40) and offset + stride * count <= len(data))
    sections = []
    for i in range(count):
        base = offset + stride * i
        kind = integer('I', base + 4)
        start = integer('Q' if wide else 'I', base + (24 if wide else 16))
        size = integer('Q' if wide else 'I', base + (32 if wide else 20))
        link = integer('I', base + (40 if wide else 24))
        entry = integer('Q' if wide else 'I', base + (56 if wide else 36))
        sections.append((kind, start, size, link, entry))
    for kind, start, size, link, entry in sections:
        if kind != 11:  # SHT_DYNSYM, not a name found in debug strings.
            continue
        need(entry == (24 if wide else 16) and size % entry == 0 and size // entry <= 1_000_000)
        need(start + size <= len(data) and link < count)
        str_kind, strings, str_size, _, _ = sections[link]
        need(str_kind == 3 and strings + str_size <= len(data))
        for at in range(start, start + size, entry):
            name = integer('I', at)
            info = integer('B', at + (4 if wide else 12))
            other = integer('B', at + (5 if wide else 13))
            index = integer('H', at + (6 if wide else 14))
            need(name < str_size)
            end = data.find(b'\0', strings + name, strings + str_size)
            need(end >= 0)
            if data[strings + name:end] == symbol:
                need(info >> 4 in (1, 2) and info & 15 == 2 and 0 < index < count and other & 3 in (0, 3))
                return
    raise ValueError('APK lacks the required defined dynamic JNI export ' + symbol.decode() + '; rebuild assembleDebug')


def verify_jni_exports(data: bytes, elf_class: int, machine: int) -> None:
    """Require the existing resolved V2 AND new source-context ABI in each fresh APK library."""
    verify_jni_export(data, elf_class, machine, SYMBOL)
    verify_jni_export(data, elf_class, machine, SOURCE_SYMBOL)


def verify_schedule_jni_exports(data: bytes, elf_class: int, machine: int) -> None:
    """Stage4a requires ALL prior entries and the actual new schedule function."""
    verify_jni_exports(data, elf_class, machine)
    verify_jni_export(data, elf_class, machine, SCHEDULE_SYMBOL)
