#!/usr/bin/env python3
"""Read only selected iOS 23A341 cache ranges; never load the whole DSC.

Analysis tooling only, not Android runtime code. Requires installed capstone.
Inputs are the .48 and .72.dyldlinkedit cache parts from the research IPSW.
"""
import argparse
import bisect
import hashlib
import json
from pathlib import Path
import struct
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN

BASE = 0x234F04000
TEXT_FILE_OFFSET = 0x4DFC000


def uleb(data, pos):
    value = shift = 0
    while pos < len(data) and shift < 64:
        byte = data[pos]
        pos += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, pos
        shift += 7
    raise ValueError('Invalid ULEB128')


def read_at(stream, offset, size):
    stream.seek(offset)
    data = stream.read(size)
    if len(data) != size:
        raise ValueError('Truncated cache range')
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('cache_dir', type=Path)
    parser.add_argument('output_dir', type=Path)
    parser.add_argument('--address', action='append', default=[], type=lambda x: int(x, 0))
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    text_path = args.cache_dir / 'dyld_shared_cache_arm64e.48'
    link_path = args.cache_dir / 'dyld_shared_cache_arm64e.72.dyldlinkedit'
    with text_path.open('rb') as text, link_path.open('rb') as link:
        head = read_at(text, TEXT_FILE_OFFSET, 32)
        if head[:4] != bytes.fromhex('cffaedfe'):
            raise ValueError('Unexpected Mach-O image; requires iOS 23A341')
        ncmd, command_size = struct.unpack_from('<II', head, 16)
        commands = read_at(text, TEXT_FILE_OFFSET + 32, command_size)
        offset = 0
        symtab = starts_command = None
        for _ in range(ncmd):
            command, size = struct.unpack_from('<II', commands, offset)
            if size < 8 or offset + size > len(commands):
                raise ValueError('Invalid Mach-O load command')
            if command == 2:
                symtab = struct.unpack_from('<IIII', commands, offset + 8)
            if command == 0x26:
                starts_command = struct.unpack_from('<II', commands, offset + 8)
            offset += size
        if symtab is None or starts_command is None:
            raise ValueError('Missing symbol/function-start tables')
        symoff, nsyms, stroff, strsize = symtab
        table = read_at(link, symoff, nsyms * 16)
        symbols = []
        for offset in range(0, len(table), 16):
            index, kind, section, desc, address = struct.unpack_from('<IBBHQ', table, offset)
            if index >= strsize:
                raise ValueError('String table index out of bounds')
            name_bytes = read_at(link, stroff + index, min(4096, strsize - index))
            if b'\0' not in name_bytes:
                raise ValueError('Unterminated or overlong symbol')
            name = name_bytes.split(b'\0', 1)[0].decode('utf-8')
            symbols.append({'address': hex(address), 'kind': kind, 'name': name})
        data = read_at(link, *starts_command)
        starts, pos, address = [], 0, BASE
        while pos < len(data):
            delta, pos = uleb(data, pos)
            if not delta:
                break
            address += delta
            starts.append(address)
        factories = [s for s in symbols if 'Factory' in s['name'] and int(s['address'], 16)]
        selected = args.address or [int(s['address'], 16) for s in factories]
        decoder = Cs(CS_ARCH_ARM64, CS_MODE_LITTLE_ENDIAN)
        for address in selected:
            index = bisect.bisect_right(starts, address) - 1
            if index < 0 or index + 1 == len(starts):
                raise ValueError('Cannot bound selected function')
            begin, end = starts[index:index + 2]
            if not BASE <= begin < end <= 0x234FFA43C:
                raise ValueError('Selected function outside this image text')
            code = read_at(text, TEXT_FILE_OFFSET + begin - BASE, end - begin)
            lines = [f'; Image: libEmbeddedSystemAUs.dylib iOS 23A341',
                     f'; Range: {begin:#x}..{end:#x}',
                     f'; Code SHA256: {hashlib.sha256(code).hexdigest()}']
            decoded = list(decoder.disasm(code, begin))
            if sum(i.size for i in decoded) != len(code):
                raise ValueError('Incomplete instruction decoding')
            lines += [f'{i.address:#x}: {i.bytes.hex():8} {i.mnemonic} {i.op_str}'
                      for i in decoded]
            (args.output_dir / f'{begin:x}.asm').write_text('\n'.join(lines) + '\n')
        result = {'image': 'libEmbeddedSystemAUs.dylib', 'base': hex(BASE),
                  'image_header_sha256': hashlib.sha256(head + commands).hexdigest(),
                  'symbol_count': nsyms, 'function_count': len(starts),
                  'factories': factories,
                  'inputs': [str(text_path), str(link_path)]}
        (args.output_dir / 'index.json').write_text(json.dumps(result, indent=2) + '\n')
        print(f'{nsyms} symbols, {len(starts)} functions, {len(selected)} bounded disassemblies')


if __name__ == '__main__':
    main()
