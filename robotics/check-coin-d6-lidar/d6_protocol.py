#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from dataclasses import dataclass
from typing import List, Optional, Callable


@dataclass
class LidarPoint:
    angle: float      # (deg)
    distance: float   # (mm)
    intensity: int


class D6Protocol:
    CMD_START = bytes([0xAA, 0x55, 0xF0, 0x0F])
    CMD_STOP = bytes([0xAA, 0x55, 0xF5, 0x0A])

    def __init__(self):
        self.buffer = bytearray()
        self.current_scan: List[LidarPoint] = []
        self.scan_callback: Optional[Callable[[List[LidarPoint]], None]] = None

    def set_scan_callback(self, callback: Optional[Callable[[List[LidarPoint]], None]]):
        self.scan_callback = callback

    def _find_header(self):
        for i in range(len(self.buffer) - 1):
            if self.buffer[i] == 0xAA and self.buffer[i + 1] == 0x55:
                return i
        return None

    def process_data(self, data: bytes):
        self.buffer.extend(data)

        while True:
            header_pos = self._find_header()
            if header_pos is None:
                if len(self.buffer) > 1:
                    self.buffer = self.buffer[-1:]
                break

            if header_pos > 0:
                self.buffer = self.buffer[header_pos:]

            # 最小パケットサイズ
            if len(self.buffer) < 10:
                break

            m_and_t = self.buffer[2]
            lsn = self.buffer[3]
            packet_size = 10 + (lsn * 3)

            if len(self.buffer) < packet_size:
                break

            packet = self.buffer[:packet_size]
            self.buffer = self.buffer[packet_size:]
            self._parse_packet(packet, m_and_t, lsn)

    def _parse_packet(self, packet: bytes, m_and_t: int, lsn: int):
        is_start_packet = (m_and_t & 0x01) == 1

        if is_start_packet:
            if self.current_scan and self.scan_callback:
                self.scan_callback(self.current_scan)
            self.current_scan = []

        if lsn == 0:
            return

        fsa = packet[4] | (packet[5] << 8)
        lsa = packet[6] | (packet[7] << 8)

        angle_fsa = (fsa >> 1) / 64.0
        angle_lsa = (lsa >> 1) / 64.0

        if lsn > 1:
            if angle_lsa < angle_fsa:
                angle_lsa += 360.0
            angle_step = (angle_lsa - angle_fsa) / (lsn - 1)
        else:
            angle_step = 0

        for i in range(lsn):
            offset = 10 + (i * 3)
            if offset + 3 > len(packet):
                break

            si_l = packet[offset]
            si_2nd = packet[offset + 1]
            si_h = packet[offset + 2]

            distance = si_h * 64 + (si_2nd >> 2)
            intensity = ((si_2nd & 0x03) << 6) | (si_l >> 2)

            angle = angle_fsa + (angle_step * i)
            if angle >= 360.0:
                angle -= 360.0

            self.current_scan.append(LidarPoint(angle=angle, distance=distance, intensity=intensity))
